package com.seckill.dis.goods.service;

import com.alibaba.fastjson.JSON;
import com.seckill.dis.common.api.cache.DLockApi;
import com.seckill.dis.common.api.cache.RedisServiceApi;
import com.seckill.dis.common.api.cache.vo.GoodsKeyPrefix;
import com.seckill.dis.common.api.cache.vo.SkKeyPrefix;
import com.seckill.dis.common.api.cache.vo.WaitlistKeyPrefix;
import com.seckill.dis.common.api.goods.GoodsServiceApi;
import com.seckill.dis.common.api.goods.vo.GoodsVo;
import com.seckill.dis.common.api.order.OrderServiceApi;
import com.seckill.dis.common.api.user.vo.UserVo;
import com.seckill.dis.common.api.waitlist.WaitlistServiceApi;
import com.seckill.dis.common.api.waitlist.vo.WaitlistResultVo;
import com.seckill.dis.common.api.waitlist.vo.WaitlistStatusVo;
import com.seckill.dis.common.domain.OrderInfo;
import com.seckill.dis.common.domain.SeckillOrder;
import com.seckill.dis.common.domain.SeckillWaitlist;
import com.seckill.dis.common.exception.GlobalException;
import com.seckill.dis.common.result.CodeMsg;
import com.seckill.dis.goods.persistence.WaitlistMapper;
import org.apache.dubbo.config.annotation.Reference;
import org.apache.dubbo.config.annotation.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;

/**
 * 候补抢购服务实现
 *
 * @author seckill
 */
@Service(interfaceClass = WaitlistServiceApi.class)
public class WaitlistServiceImpl implements WaitlistServiceApi {

    private static final Logger logger = LoggerFactory.getLogger(WaitlistServiceImpl.class);

    /** 候补条目默认过期时间: 24小时 */
    private static final int WAITLIST_EXPIRE_SECONDS = 86400;

    /** 补库存分布式锁过期时间: 30秒 */
    private static final int LOCK_EXPIRE_MS = 30000;

    @Autowired
    private WaitlistMapper waitlistMapper;

    @Autowired
    private GoodsServiceApi goodsService;

    @Reference(interfaceClass = OrderServiceApi.class)
    private OrderServiceApi orderService;

    @Reference(interfaceClass = RedisServiceApi.class)
    private RedisServiceApi redisService;

    @Reference(interfaceClass = DLockApi.class)
    private DLockApi dLock;

    /**
     * 加入候补抢购队列
     */
    @Override
    public WaitlistResultVo joinWaitlist(UserVo user, long goodsId) {
        // 1. 校验商品存在且允许候补
        GoodsVo goods = goodsService.getGoodsVoByGoodsId(goodsId);
        if (goods == null) {
            throw new GlobalException(CodeMsg.WL_GOODS_NOT_ALLOW);
        }
        if (!goods.isAllowWaitlist()) {
            throw new GlobalException(CodeMsg.WL_GOODS_NOT_ALLOW);
        }

        // 2. 检查用户是否已有秒杀订单（同一用户同一商品只能有一条有效候补或订单）
        SeckillOrder existingOrder = orderService
                .getSeckillOrderByUserIdAndGoodsId(user.getUuid(), goodsId);
        if (existingOrder != null) {
            throw new GlobalException(CodeMsg.REPEATE_SECKILL);
        }

        // 3. 检查 Redis ZSET: 用户是否已在候补队列中（幂等）
        Double existingScore = redisService.zscore(
                WaitlistKeyPrefix.WL_QUEUE, "" + goodsId, "" + user.getUuid());
        if (existingScore != null) {
            throw new GlobalException(CodeMsg.WL_ALREADY_JOINED);
        }

        // 4. 检查 DB 中是否有有效的候补记录
        SeckillWaitlist existing = waitlistMapper
                .getByUserIdAndGoodsId(user.getUuid(), goodsId);
        if (existing != null && existing.getStatus() == 0) {
            throw new GlobalException(CodeMsg.WL_ALREADY_JOINED);
        }

        // 5. 加入 Redis ZSET（score = 当前时间戳，保证 FIFO）
        long now = System.currentTimeMillis();
        redisService.zadd(WaitlistKeyPrefix.WL_QUEUE, "" + goodsId,
                now, "" + user.getUuid());

        // 6. 计算排位（0-based rank + 1）
        long queueSize = redisService.zcard(WaitlistKeyPrefix.WL_QUEUE, "" + goodsId);
        Long rank = redisService.zrank(WaitlistKeyPrefix.WL_QUEUE,
                "" + goodsId, "" + user.getUuid());
        int position = (rank != null) ? rank.intValue() + 1 : (int) queueSize;

        // 7. 在 Redis Hash 中存储详情
        Map<String, String> detailMap = new HashMap<>();
        detailMap.put("status", "0");
        detailMap.put("joinTime", String.valueOf(now));
        detailMap.put("position", String.valueOf(position));
        redisService.hset(WaitlistKeyPrefix.WL_DETAIL, "" + goodsId,
                "" + user.getUuid(), JSON.toJSONString(detailMap));

        // 8. 持久化到 DB
        Date expireDate = new Date(now + WAITLIST_EXPIRE_SECONDS * 1000L);
        long ticketNo;

        if (existing != null) {
            // 之前有已取消/已过期的记录，重新激活
            ticketNo = existing.getId();
            waitlistMapper.updateStatus(user.getUuid(), goodsId, 0);
        } else {
            SeckillWaitlist wl = new SeckillWaitlist();
            wl.setUserId(user.getUuid());
            wl.setGoodsId(goodsId);
            wl.setStatus(0);
            wl.setPosition(position);
            wl.setCreateDate(new Date(now));
            wl.setExpireDate(expireDate);
            ticketNo = waitlistMapper.insert(wl);
        }

        // 更新 Hash 中的 ticketNo
        detailMap.put("ticketNo", String.valueOf(ticketNo));
        redisService.hset(WaitlistKeyPrefix.WL_DETAIL, "" + goodsId,
                "" + user.getUuid(), JSON.toJSONString(detailMap));

        // 9. 构造返回结果
        WaitlistResultVo result = new WaitlistResultVo();
        result.setTicketNo(ticketNo);
        result.setPosition(position);
        result.setExpireTime(expireDate);
        result.setQueueSize(queueSize);
        return result;
    }

    /**
     * 查询候补状态
     */
    @Override
    public WaitlistStatusVo getWaitlistStatus(Long userId, long goodsId) {
        WaitlistStatusVo vo = new WaitlistStatusVo();

        // 1. 先从 Redis Hash 查
        String detailJson = redisService.hget(
                WaitlistKeyPrefix.WL_DETAIL, "" + goodsId, "" + userId);
        if (detailJson != null) {
            @SuppressWarnings("unchecked")
            Map<String, String> detail = JSON.parseObject(detailJson, Map.class);
            int status = Integer.parseInt(detail.get("status"));
            vo.setStatus(status);
            vo.setTicketNo(Long.parseLong(detail.getOrDefault("ticketNo", "0")));

            if (status == 0) {
                // 排队中：从 ZSET 获取实时排位
                Long rank = redisService.zrank(
                        WaitlistKeyPrefix.WL_QUEUE, "" + goodsId, "" + userId);
                vo.setPosition(rank != null ? rank.intValue() + 1 : -1);
                vo.setQueueSize(redisService.zcard(
                        WaitlistKeyPrefix.WL_QUEUE, "" + goodsId));
            } else if (status == 1) {
                // 已转单：从 DB 获取 orderId
                SeckillWaitlist wl = waitlistMapper
                        .getByUserIdAndGoodsId(userId, goodsId);
                if (wl != null && wl.getOrderId() != null) {
                    vo.setOrderId(wl.getOrderId());
                }
            }
            return vo;
        }

        // 2. Redis 未命中，回退到 DB
        SeckillWaitlist wl = waitlistMapper.getByUserIdAndGoodsId(userId, goodsId);
        if (wl == null) {
            vo.setStatus(-1); // 不存在
            return vo;
        }
        vo.setStatus(wl.getStatus());
        vo.setTicketNo(wl.getId());
        if (wl.getStatus() == 0) {
            Long rank = redisService.zrank(
                    WaitlistKeyPrefix.WL_QUEUE, "" + goodsId, "" + userId);
            vo.setPosition(rank != null ? rank.intValue() + 1 : -1);
            vo.setQueueSize(redisService.zcard(
                    WaitlistKeyPrefix.WL_QUEUE, "" + goodsId));
        } else if (wl.getStatus() == 1 && wl.getOrderId() != null) {
            vo.setOrderId(wl.getOrderId());
        }
        return vo;
    }

    /**
     * 取消候补
     */
    @Override
    public boolean cancelWaitlist(UserVo user, long goodsId) {
        // 1. 从 Redis ZSET 移除
        redisService.zrem(WaitlistKeyPrefix.WL_QUEUE, "" + goodsId, "" + user.getUuid());

        // 2. 从 Redis Hash 移除
        redisService.hdel(WaitlistKeyPrefix.WL_DETAIL, "" + goodsId, "" + user.getUuid());

        // 3. 更新 DB 状态为已取消(2)
        int affected = waitlistMapper.updateStatus(user.getUuid(), goodsId, 2);
        return affected > 0;
    }

    /**
     * 处理补库存后的候补转单
     * 通过分布式锁保证同一商品同一时刻只有一个线程在处理
     */
    @Override
    public int processReplenishment(long goodsId, int replenishCount) {
        String lockKey = WaitlistKeyPrefix.WL_REPLENISH_LOCK.getPrefix() + goodsId;
        String lockValue = UUID.randomUUID().toString();

        // 1. 获取分布式锁
        boolean locked = dLock.lock(lockKey, lockValue, LOCK_EXPIRE_MS);
        if (!locked) {
            logger.warn("processReplenishment: could not acquire lock for goodsId={}", goodsId);
            return 0;
        }

        int converted = 0;
        try {
            // 2. 从 DB 获取排队中的候补列表（按加入时间升序，与 ZSET FIFO 一致）
            List<SeckillWaitlist> waitingList = waitlistMapper
                    .getWaitingListByGoodsId(goodsId, replenishCount);

            for (SeckillWaitlist entry : waitingList) {
                if (converted >= replenishCount) {
                    break;
                }

                // 3. 验证用户仍在 Redis ZSET 中（未取消）
                Double score = redisService.zscore(
                        WaitlistKeyPrefix.WL_QUEUE, "" + goodsId, "" + entry.getUserId());
                if (score == null) {
                    // 用户已取消或过期，清理 DB
                    waitlistMapper.updateStatus(entry.getUserId(), goodsId, 2);
                    continue;
                }

                // 4. 检查用户是否已有订单（幂等）
                SeckillOrder existingOrder = orderService
                        .getSeckillOrderByUserIdAndGoodsId(entry.getUserId(), goodsId);
                if (existingOrder != null) {
                    // 已有订单，移除候补
                    removeFromWaitlistRedis(goodsId, entry.getUserId());
                    waitlistMapper.updateStatusToOrdered(entry.getId(), existingOrder.getOrderId());
                    continue;
                }

                // 5. 检查库存
                GoodsVo goods = goodsService.getGoodsVoByGoodsId(goodsId);
                if (goods == null || goods.getStockCount() <= 0) {
                    logger.warn("Stock depleted during replenishment processing for goodsId={}", goodsId);
                    break;
                }

                // 6. 构造用户对象，执行减库存 + 创建订单
                UserVo user = new UserVo();
                user.setUuid(entry.getUserId());

                boolean stockReduced = goodsService.reduceStock(goods);
                if (!stockReduced) {
                    logger.warn("reduceStock failed for goodsId={} during replenishment", goodsId);
                    break;
                }

                OrderInfo order = orderService.createOrder(user, goods);
                if (order == null) {
                    logger.error("createOrder failed for userId={}, goodsId={}",
                            entry.getUserId(), goodsId);
                    break;
                }

                // 7. 标记为已转单（CAS: WHERE status=0 防止取消竞争）
                int affected = waitlistMapper.updateStatusToOrdered(entry.getId(), order.getId());
                if (affected == 0) {
                    // 已被取消或已处理，跳过
                    logger.info("Waitlist entry {} already processed/cancelled, skipping", entry.getId());
                    continue;
                }

                // 8. 从 Redis ZSET 移除
                removeFromWaitlistRedis(goodsId, entry.getUserId());

                // 9. 更新 Redis Hash 状态为已转单
                Map<String, String> detailMap = new HashMap<>();
                detailMap.put("status", "1");
                detailMap.put("orderId", String.valueOf(order.getId()));
                detailMap.put("ticketNo", String.valueOf(entry.getId()));
                redisService.hset(WaitlistKeyPrefix.WL_DETAIL, "" + goodsId,
                        "" + entry.getUserId(), JSON.toJSONString(detailMap));

                converted++;
                logger.info("Replenishment order created: userId={}, goodsId={}, orderId={}",
                        entry.getUserId(), goodsId, order.getId());
            }

            // 10. 刷新 Redis 库存缓存
            GoodsVo updatedGoods = goodsService.getGoodsVoByGoodsId(goodsId);
            if (updatedGoods != null) {
                redisService.set(GoodsKeyPrefix.GOODS_STOCK, "" + goodsId,
                        updatedGoods.getStockCount());
                // 如果库存大于0，清除"商品售罄"标记
                if (updatedGoods.getStockCount() > 0) {
                    redisService.delete(SkKeyPrefix.GOODS_SK_OVER, "" + goodsId);
                }
            }

        } finally {
            dLock.unlock(lockKey, lockValue);
        }

        return converted;
    }

    /**
     * 从 Redis ZSET 和 Hash 中移除用户的候补信息
     */
    private void removeFromWaitlistRedis(long goodsId, long userId) {
        redisService.zrem(WaitlistKeyPrefix.WL_QUEUE, "" + goodsId, "" + userId);
        // 注意: Hash 条目由调用方决定是删除还是更新为已转单状态
    }
}
