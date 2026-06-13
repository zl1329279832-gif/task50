package com.seckill.dis.mq.receiver;

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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * MqConsumer 回归测试
 * <p>
 * 覆盖:
 * - 正常秒杀消费: 调用 seckill + setSeckillProcessed
 * - MQ 重复消费: 已有订单时幂等跳过，不再调用 seckill
 * - 售罄后消费: 库存为 0 时跳过秒杀，标记已消费
 * - 服务重启后重投: 消息重新消费的幂等性
 *
 * @author seckill
 */
public class MqConsumerTest {

    private MqConsumer consumer;

    private GoodsServiceApi goodsService;
    private OrderServiceApi orderService;
    private SeckillServiceApi seckillService;
    private RedisServiceApi redisService;

    private UserVo testUser;
    private GoodsVo testGoods;
    private SkMessage testMessage;
    private static final long GOODS_ID = 1L;
    private static final long USER_ID = 100L;

    @Before
    public void setUp() throws Exception {
        goodsService = mock(GoodsServiceApi.class);
        orderService = mock(OrderServiceApi.class);
        seckillService = mock(SeckillServiceApi.class);
        redisService = mock(RedisServiceApi.class);

        testUser = new UserVo();
        testUser.setUuid(USER_ID);
        testUser.setPhone(18342390420L);

        testGoods = new GoodsVo();
        testGoods.setId(GOODS_ID);
        testGoods.setGoodsName("iphoneX");
        testGoods.setSeckillPrice(0.01);
        testGoods.setStockCount(10);

        testMessage = new SkMessage();
        testMessage.setUser(testUser);
        testMessage.setGoodsId(GOODS_ID);

        consumer = new MqConsumer();
        injectField(consumer, "goodsService", goodsService);
        injectField(consumer, "orderService", orderService);
        injectField(consumer, "seckillService", seckillService);
        injectField(consumer, "redisService", redisService);
    }

    // ─────────── 正常秒杀消费 ───────────

    /**
     * 正常消费: 调用 seckill 并标记 setSeckillProcessed
     */
    @Test
    public void testReceive_normalSeckill_callsSeckillAndMarksProcessed() {
        when(goodsService.getGoodsVoByGoodsId(GOODS_ID)).thenReturn(testGoods);
        when(redisService.get(eq(OrderKeyPrefix.SK_ORDER), anyString(), eq(SeckillOrder.class)))
                .thenReturn(null);
        when(orderService.getSeckillOrderByUserIdAndGoodsId(USER_ID, GOODS_ID)).thenReturn(null);
        when(seckillService.seckill(any(UserVo.class), any(GoodsVo.class)))
                .thenReturn(new OrderInfo());

        consumer.receiveSkInfo(testMessage);

        verify(seckillService, times(1)).seckill(testUser, testGoods);
        verify(seckillService, times(1)).setSeckillProcessed(USER_ID, GOODS_ID);
    }

    // ─────────── MQ 重复消费 ───────────

    /**
     * 重复消费: 已有订单时不再调用 seckill，但仍标记 processed
     */
    @Test
    public void testReceive_duplicateOrder_skipsSeckilButMarksProcessed() {
        when(goodsService.getGoodsVoByGoodsId(GOODS_ID)).thenReturn(testGoods);

        SeckillOrder existingOrder = new SeckillOrder();
        existingOrder.setUserId(USER_ID);
        existingOrder.setGoodsId(GOODS_ID);
        existingOrder.setOrderId(42L);
        when(redisService.get(eq(OrderKeyPrefix.SK_ORDER), anyString(), eq(SeckillOrder.class)))
                .thenReturn(existingOrder);

        consumer.receiveSkInfo(testMessage);

        verify(seckillService, never()).seckill(any(), any());
        verify(seckillService, times(1)).setSeckillProcessed(USER_ID, GOODS_ID);
    }

    /**
     * 重复消费: Redis 缓存未命中但 DB 有订单，不再调用 seckill
     */
    @Test
    public void testReceive_duplicateOrder_dbFallback() {
        when(goodsService.getGoodsVoByGoodsId(GOODS_ID)).thenReturn(testGoods);
        when(redisService.get(eq(OrderKeyPrefix.SK_ORDER), anyString(), eq(SeckillOrder.class)))
                .thenReturn(null);

        SeckillOrder dbOrder = new SeckillOrder();
        dbOrder.setUserId(USER_ID);
        dbOrder.setGoodsId(GOODS_ID);
        dbOrder.setOrderId(42L);
        when(orderService.getSeckillOrderByUserIdAndGoodsId(USER_ID, GOODS_ID)).thenReturn(dbOrder);

        consumer.receiveSkInfo(testMessage);

        verify(seckillService, never()).seckill(any(), any());
        verify(seckillService, times(1)).setSeckillProcessed(USER_ID, GOODS_ID);
    }

    // ─────────── 售罄后消费 ───────────

    /**
     * 库存为 0: 不调用 seckill，标记 processed
     */
    @Test
    public void testReceive_stockZero_marksProcessedWithoutSeckill() {
        GoodsVo soldOutGoods = new GoodsVo();
        soldOutGoods.setId(GOODS_ID);
        soldOutGoods.setStockCount(0);
        when(goodsService.getGoodsVoByGoodsId(GOODS_ID)).thenReturn(soldOutGoods);

        consumer.receiveSkInfo(testMessage);

        verify(seckillService, never()).seckill(any(), any());
        verify(seckillService, times(1)).setSeckillProcessed(USER_ID, GOODS_ID);
    }

    /**
     * 库存为负数: 不调用 seckill，标记 processed
     */
    @Test
    public void testReceive_stockNegative_marksProcessedWithoutSeckill() {
        GoodsVo overSoldGoods = new GoodsVo();
        overSoldGoods.setId(GOODS_ID);
        overSoldGoods.setStockCount(-1);
        when(goodsService.getGoodsVoByGoodsId(GOODS_ID)).thenReturn(overSoldGoods);

        consumer.receiveSkInfo(testMessage);

        verify(seckillService, never()).seckill(any(), any());
        verify(seckillService, times(1)).setSeckillProcessed(USER_ID, GOODS_ID);
    }

    // ─────────── 服务重启后 MQ 重投 ───────────

    /**
     * 服务重启后同一消息重新投递: 幂等处理
     */
    @Test
    public void testReceive_redeliveryAfterRestart_idempotent() {
        // 第一次消费: 正常秒杀
        when(goodsService.getGoodsVoByGoodsId(GOODS_ID)).thenReturn(testGoods);
        when(redisService.get(eq(OrderKeyPrefix.SK_ORDER), anyString(), eq(SeckillOrder.class)))
                .thenReturn(null);
        when(orderService.getSeckillOrderByUserIdAndGoodsId(USER_ID, GOODS_ID)).thenReturn(null);
        when(seckillService.seckill(any(UserVo.class), any(GoodsVo.class)))
                .thenReturn(new OrderInfo());

        consumer.receiveSkInfo(testMessage);

        // 第二次消费（重投）: 发现已有订单
        SeckillOrder existing = new SeckillOrder();
        existing.setOrderId(1L);
        when(redisService.get(eq(OrderKeyPrefix.SK_ORDER), anyString(), eq(SeckillOrder.class)))
                .thenReturn(existing);

        consumer.receiveSkInfo(testMessage);

        // seckill 只被调用一次
        verify(seckillService, times(1)).seckill(any(), any());
        // setSeckillProcessed 被调用两次（每次消费都标记）
        verify(seckillService, times(2)).setSeckillProcessed(USER_ID, GOODS_ID);
    }

    // ─────────── 辅助方法 ───────────

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
