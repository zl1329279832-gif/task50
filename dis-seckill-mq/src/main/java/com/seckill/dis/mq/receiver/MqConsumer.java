package com.seckill.dis.mq.receiver;

import com.seckill.dis.common.api.cache.DLockApi;
import com.seckill.dis.common.api.cache.RedisServiceApi;
import com.seckill.dis.common.api.cache.vo.GoodsKeyPrefix;
import com.seckill.dis.common.api.cache.vo.OrderKeyPrefix;
import com.seckill.dis.common.api.goods.GoodsServiceApi;
import com.seckill.dis.common.api.goods.vo.GoodsVo;
import com.seckill.dis.common.api.mq.vo.SkMessage;
import com.seckill.dis.common.api.order.OrderServiceApi;
import com.seckill.dis.common.api.seckill.SeckillServiceApi;
import com.seckill.dis.common.api.user.vo.UserVo;
import com.seckill.dis.common.domain.SeckillOrder;
import com.seckill.dis.mq.config.MQConfig;
import org.apache.dubbo.config.annotation.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * MQ消息接收者, 消费者
 * 消费者绑定在队列监听，既可以接收到队列中的消息
 *
 * @author noodle
 */
@Service
public class MqConsumer {

    private static Logger logger = LoggerFactory.getLogger(MqConsumer.class);

    /** 秒杀消费分布式锁过期时间: 10 秒 */
    private static final int SK_LOCK_EXPIRE_MS = 10000;

    @Reference(interfaceClass = GoodsServiceApi.class)
    GoodsServiceApi goodsService;

    @Reference(interfaceClass = OrderServiceApi.class)
    OrderServiceApi orderService;

    @Reference(interfaceClass = SeckillServiceApi.class)
    SeckillServiceApi seckillService;

    @Reference(interfaceClass = RedisServiceApi.class)
    RedisServiceApi redisService;

    @Reference(interfaceClass = DLockApi.class)
    DLockApi dLock;

    /**
     * 处理收到的秒杀成功信息（核心业务实现）
     * <p>
     * 通过分布式锁保证同一用户同一商品的消息在任意时刻只有一个线程在处理，
     * 从而防止 MQ 重投或重复消息导致的重复下单。
     *
     * @param message
     */
    @RabbitListener(queues = MQConfig.SECKILL_QUEUE)
    public void receiveSkInfo(SkMessage message) {
        logger.info("MQ receive a message: " + message);

        UserVo user = message.getUser();
        long goodsId = message.getGoodsId();

        // ── 1. 分布式锁: 同一 user+goods 同一时刻只允许一个消费者处理 ──
        String lockKey = "sk_consumer:" + user.getUuid() + "_" + goodsId;
        String lockValue = UUID.randomUUID().toString();
        boolean locked = dLock.lock(lockKey, lockValue, SK_LOCK_EXPIRE_MS);
        if (!locked) {
            // 拿不到锁说明另一线程正在处理同一条消息，抛出异常让 RabbitMQ 重投
            logger.warn("MQ consumer: could not acquire lock for user={}, goods={}, will retry",
                    user.getUuid(), goodsId);
            throw new RuntimeException("Consumer lock not acquired, retry later");
        }

        try {
            // ── 2. 幂等检查: 是否已有秒杀订单 ──
            SeckillOrder order = getSkOrderByUserIdAndGoodsId(user.getUuid(), goodsId);
            if (order != null) {
                // 已处理过（可能是重投消息），标记 processed 后直接返回
                seckillService.setSeckillProcessed(user.getUuid(), goodsId);
                return;
            }

            // ── 3. 库存检查 ──
            GoodsVo goods = goodsService.getGoodsVoByGoodsId(goodsId);
            if (goods == null || goods.getStockCount() <= 0) {
                seckillService.setSeckillProcessed(user.getUuid(), goodsId);
                return;
            }

            // ── 4. 执行秒杀: 减库存 + 创建订单 ──
            seckillService.seckill(user, goods);

            // ── 5. 无论成功与否，标记此 user+goods 已被消费者处理 ──
            seckillService.setSeckillProcessed(user.getUuid(), goodsId);

        } finally {
            dLock.unlock(lockKey, lockValue);
        }
    }

    /**
     * 通过用户id与商品id从订单列表中获取订单信息，这个地方用了唯一索引（unique index!!!!!）
     * <p>
     * 优化，不同每次都去数据库中读取秒杀订单信息，而是在第一次生成秒杀订单成功后，
     * 将订单存储在redis中，再次读取订单信息的时候就直接从redis中读取
     *
     * @param userId
     * @param goodsId
     * @return 秒杀订单信息
     */
    private SeckillOrder getSkOrderByUserIdAndGoodsId(Long userId, long goodsId) {

        // 从redis中取缓存，减少数据库的访问
        SeckillOrder seckillOrder = redisService.get(OrderKeyPrefix.SK_ORDER, ":" + userId + "_" + goodsId, SeckillOrder.class);
        if (seckillOrder != null) {
            return seckillOrder;
        }
        return orderService.getSeckillOrderByUserIdAndGoodsId(userId, goodsId);
    }
}
