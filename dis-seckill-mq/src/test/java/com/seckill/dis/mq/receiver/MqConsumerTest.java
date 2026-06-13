package com.seckill.dis.mq.receiver;

import com.seckill.dis.common.api.cache.DLockApi;
import com.seckill.dis.common.api.cache.RedisServiceApi;
import com.seckill.dis.common.api.cache.vo.OrderKeyPrefix;
import com.seckill.dis.common.api.goods.GoodsServiceApi;
import com.seckill.dis.common.api.goods.vo.GoodsVo;
import com.seckill.dis.common.api.mq.vo.SkMessage;
import com.seckill.dis.common.api.order.OrderServiceApi;
import com.seckill.dis.common.api.seckill.SeckillServiceApi;
import com.seckill.dis.common.api.user.vo.UserVo;
import com.seckill.dis.common.domain.OrderInfo;
import com.seckill.dis.common.domain.SeckillOrder;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * MqConsumer 回归测试
 * <p>
 * 覆盖:
 * - MQ 重复消费幂等性: 同一条消息被投递两次，只生成一个订单
 * - 并发消费幂等性: 10 个线程同时消费同一条消息，只有一个执行 seckill
 * - 库存耗尽时标记 processed
 * - 分布式锁获取失败时抛出异常 (触发 RabbitMQ 重投)
 *
 * @author seckill
 */
public class MqConsumerTest {

    private MqConsumer mqConsumer;

    private GoodsServiceApi goodsService;
    private OrderServiceApi orderService;
    private SeckillServiceApi seckillService;
    private RedisServiceApi redisService;
    private DLockApi dLock;

    private UserVo testUser;
    private GoodsVo testGoods;
    private static final long GOODS_ID = 1L;
    private static final long USER_ID = 100L;

    @Before
    public void setUp() throws Exception {
        goodsService = mock(GoodsServiceApi.class);
        orderService = mock(OrderServiceApi.class);
        seckillService = mock(SeckillServiceApi.class);
        redisService = mock(RedisServiceApi.class);
        dLock = mock(DLockApi.class);

        testUser = new UserVo();
        testUser.setUuid(USER_ID);
        testUser.setPhone(18342390420L);

        testGoods = new GoodsVo();
        testGoods.setId(GOODS_ID);
        testGoods.setGoodsName("iphoneX");
        testGoods.setSeckillPrice(0.01);
        testGoods.setStockCount(10);

        mqConsumer = new MqConsumer();
        injectField(mqConsumer, "goodsService", goodsService);
        injectField(mqConsumer, "orderService", orderService);
        injectField(mqConsumer, "seckillService", seckillService);
        injectField(mqConsumer, "redisService", redisService);
        injectField(mqConsumer, "dLock", dLock);
    }

    // ─────────── MQ 重复消费幂等性 ───────────

    /**
     * 同一消息被投递两次: 第二次检测到已有订单，不再调用 seckill
     */
    @Test
    public void testDuplicateConsumption_idempotency() {
        SkMessage msg = createMessage();

        when(dLock.lock(anyString(), anyString(), anyInt())).thenReturn(true);
        when(dLock.unlock(anyString(), anyString())).thenReturn(true);

        // 第一次: 无订单; 第二次: 有订单
        when(orderService.getSeckillOrderByUserIdAndGoodsId(USER_ID, GOODS_ID))
                .thenReturn(null)
                .thenReturn(createSeckillOrder(1L));

        when(redisService.get(eq(OrderKeyPrefix.SK_ORDER), anyString(), eq(SeckillOrder.class)))
                .thenReturn(null)
                .thenReturn(createSeckillOrder(1L));

        when(goodsService.getGoodsVoByGoodsId(GOODS_ID)).thenReturn(testGoods);
        when(seckillService.seckill(any(UserVo.class), any(GoodsVo.class)))
                .thenReturn(createOrderInfo(1L));

        // 第一次消费 — 正常处理
        mqConsumer.receiveSkInfo(msg);
        verify(seckillService, times(1)).seckill(testUser, testGoods);
        verify(seckillService, times(1)).setSeckillProcessed(USER_ID, GOODS_ID);

        // 第二次消费 (重投) — 幂等拦截
        mqConsumer.receiveSkInfo(msg);
        verify(seckillService, times(1)).seckill(testUser, testGoods); // 仍然 1 次
        // processed 被调用了两次 (幂等路径也设置)
        verify(seckillService, times(2)).setSeckillProcessed(USER_ID, GOODS_ID);
    }

    /**
     * 10 个线程同时消费同一条消息: 分布式锁保证只有一个执行 seckill
     */
    @Test
    public void testConcurrentDuplicate_onlyOneProcesses() throws Exception {
        SkMessage msg = createMessage();

        AtomicInteger lockCount = new AtomicInteger(0);
        when(dLock.lock(anyString(), anyString(), anyInt()))
                .thenAnswer(inv -> lockCount.getAndIncrement() == 0);
        when(dLock.unlock(anyString(), anyString())).thenReturn(true);

        when(orderService.getSeckillOrderByUserIdAndGoodsId(USER_ID, GOODS_ID)).thenReturn(null);
        when(redisService.get(eq(OrderKeyPrefix.SK_ORDER), anyString(), eq(SeckillOrder.class)))
                .thenReturn(null);
        when(goodsService.getGoodsVoByGoodsId(GOODS_ID)).thenReturn(testGoods);
        when(seckillService.seckill(any(UserVo.class), any(GoodsVo.class)))
                .thenReturn(createOrderInfo(1L));

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger processed = new AtomicInteger(0);
        AtomicInteger rejected = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    mqConsumer.receiveSkInfo(msg);
                    processed.incrementAndGet();
                } catch (RuntimeException e) {
                    rejected.incrementAndGet();
                } catch (Exception e) {
                    rejected.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals("只有 1 个线程应成功处理", 1, processed.get());
        assertEquals("其余 9 个线程应被锁拒绝", 9, rejected.get());
        verify(seckillService, times(1)).seckill(testUser, testGoods);
    }

    /**
     * 库存耗尽时: 不执行 seckill，标记 processed
     */
    @Test
    public void testStockDepleted_marksProcessed() {
        SkMessage msg = createMessage();

        when(dLock.lock(anyString(), anyString(), anyInt())).thenReturn(true);
        when(dLock.unlock(anyString(), anyString())).thenReturn(true);

        when(redisService.get(eq(OrderKeyPrefix.SK_ORDER), anyString(), eq(SeckillOrder.class)))
                .thenReturn(null);
        when(orderService.getSeckillOrderByUserIdAndGoodsId(USER_ID, GOODS_ID)).thenReturn(null);

        GoodsVo depleted = new GoodsVo();
        depleted.setId(GOODS_ID);
        depleted.setStockCount(0);
        when(goodsService.getGoodsVoByGoodsId(GOODS_ID)).thenReturn(depleted);

        mqConsumer.receiveSkInfo(msg);

        verify(seckillService, never()).seckill(any(), any());
        verify(seckillService).setSeckillProcessed(USER_ID, GOODS_ID);
    }

    /**
     * 分布式锁获取失败: 抛出 RuntimeException，触发 RabbitMQ 消息重投
     */
    @Test(expected = RuntimeException.class)
    public void testLockFail_throwsException() {
        SkMessage msg = createMessage();
        when(dLock.lock(anyString(), anyString(), anyInt())).thenReturn(false);

        mqConsumer.receiveSkInfo(msg);
    }

    /**
     * seckill 返回 null (减库存失败): 仍然标记 processed
     */
    @Test
    public void testSeckillReturnsNull_marksProcessed() {
        SkMessage msg = createMessage();

        when(dLock.lock(anyString(), anyString(), anyInt())).thenReturn(true);
        when(dLock.unlock(anyString(), anyString())).thenReturn(true);

        when(redisService.get(eq(OrderKeyPrefix.SK_ORDER), anyString(), eq(SeckillOrder.class)))
                .thenReturn(null);
        when(orderService.getSeckillOrderByUserIdAndGoodsId(USER_ID, GOODS_ID)).thenReturn(null);
        when(goodsService.getGoodsVoByGoodsId(GOODS_ID)).thenReturn(testGoods);

        // seckill 返回 null (减库存失败)
        when(seckillService.seckill(any(UserVo.class), any(GoodsVo.class))).thenReturn(null);

        mqConsumer.receiveSkInfo(msg);

        verify(seckillService).seckill(testUser, testGoods);
        verify(seckillService).setSeckillProcessed(USER_ID, GOODS_ID);
    }

    // ─────────── 辅助方法 ───────────

    private SkMessage createMessage() {
        SkMessage msg = new SkMessage();
        msg.setUser(testUser);
        msg.setGoodsId(GOODS_ID);
        return msg;
    }

    private SeckillOrder createSeckillOrder(long orderId) {
        SeckillOrder order = new SeckillOrder();
        order.setUserId(USER_ID);
        order.setGoodsId(GOODS_ID);
        order.setOrderId(orderId);
        return order;
    }

    private OrderInfo createOrderInfo(long id) {
        OrderInfo info = new OrderInfo();
        info.setId(id);
        info.setUserId(USER_ID);
        info.setGoodsId(GOODS_ID);
        return info;
    }

    private static void injectField(Object target, String fieldName, Object value) throws Exception {
        Class<?> clazz = target.getClass();
        while (clazz != null) {
            try {
                Field field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName);
    }
}
