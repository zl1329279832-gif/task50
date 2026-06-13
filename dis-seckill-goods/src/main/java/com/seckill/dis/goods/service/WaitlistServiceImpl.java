package com.seckill.dis.goods.service;

import com.alibaba.fastjson.JSON;
import com.seckill.dis.common.api.cache.DLockApi;
import com.seckill.dis.common.api.cache.RedisServiceApi;
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
 */
@Service(interfaceClass = WaitlistServiceApi.class)
public class WaitlistServiceImpl implements WaitlistServiceApi {

    private static Logger logger = LoggerFactory.getLogger(WaitlistServiceImpl.class);

    /** 候补过期时间：24小时 */
    private static final long WAITLIST_EXPIRE_MILLIS = 24 * 60 * 60 * 1000L;

    @Autowired
    WaitlistMapper waitlistMapper;

    @Autowired
    GoodsServiceApi goodsService;

    @Reference(interfaceClass = OrderServiceApi.class)
    OrderServiceApi orderService;

    @Reference(interfaceClass = RedisServiceApi.class)
    RedisServiceApi redisService;

    @Reference(interfaceClass = DLockApi.class)
    DLockApi dLock;

    @Override
    public WaitlistResultVo joinWaitlist(UserVo user, long goodsId) {
        // 1. 校验商品是否存在且允许候补
        GoodsVo goods = goodsService.getGoodsVoByGoodsId(goodsId);
        if (goods == null) {
            throw new GlobalException(CodeMsg.WAITLIST_GOODS_NOT_EXIST);
        }
        if (!goods.isAllowWaitlist()) {
            throw new GlobalException(CodeMsg.WAITLIST_NOT_ALLOWED);
        }

        // 2. 校验是否已有秒杀订单
        SeckillOrder existingOrder = orderService.getSeckillOrderByUserIdAndGoodsId(user.getUuid(), goodsId);
        if (existingOrder != null) {
            throw new GlobalException(CodeMsg.WAITLIST_HAS_ORDER);
        }

        // 3. 校验是否已在候补队列（Redis ZSET）
        Double score = redisService.zscore(WaitlistKeyPrefix.WL_QUEUE, "" + goodsId, "" + user.getUuid());
        if (score != null) {
            throw new GlobalException(CodeMsg.WAITLIST_DUPLICATE);
        }

        // 4. 校验DB中是否有有效候补记录
        SeckillWaitlist existing = waitlistMapper.getByUserIdAndGoodsId(user.getUuid(), goodsId);
        if (existing != null && existing.getStatus() == 0) {
            throw new GlobalException(CodeMsg.WAITLIST_DUPLICATE);
        }

        // 5. 加入 Redis ZSET 队列
        double joinScore = System.currentTimeMillis();
        redisService.zadd(WaitlistKeyPrefix.WL_QUEUE, "" + goodsId, joinScore, "" + user.getUuid());

        // 6. 获取排位信息
        long queueSize = redisService.zcard(WaitlistKeyPrefix.WL_QUEUE, "" + goodsId);
        Long rank = redisService.zrank(WaitlistKeyPrefix.WL_QUEUE, "" + goodsId, "" + user.getUuid());
        int position = (rank != null) ? (int) (rank + 1) : 1;

        // 7. DB操作：复用已取消记录或新建
        long ticketNo;
        if (existing != null && existing.getStatus() == 2) {
            // 复用已取消的记录
            waitlistMapper.updateStatus(user.getUuid(), goodsId, 0);
            ticketNo = existing.getId();
        } else {
            // 新建候补记录
            SeckillWaitlist wl = new SeckillWaitlist();
            wl.setUserId(user.getUuid());
            wl.setGoodsId(goodsId);
            wl.setStatus(0);
            wl.setCreateTime(new Date());
            ticketNo = waitlistMapper.insert(wl);
        }

        // 8. 写入候补详情到 Redis Hash
        Map<String, String> detail = new HashMap<>();
        detail.put("ticketNo", "" + ticketNo);
        detail.put("status", "0");
        redisService.hset(WaitlistKeyPrefix.WL_DETAIL, "" + goodsId, "" + user.getUuid(), JSON.toJSONString(detail));

        // 9. 构建返回结果
        WaitlistResultVo result = new WaitlistResultVo();
        result.setTicketNo(ticketNo);
        result.setPosition(position);
        result.setQueueSize(queueSize);
        result.setExpireTime(new Date(System.currentTimeMillis() + WAITLIST_EXPIRE_MILLIS));
        return result;
    }

    @Override
    public boolean cancelWaitlist(UserVo user, long goodsId) {
        // 1. 从 Redis ZSET 移除
        redisService.zrem(WaitlistKeyPrefix.WL_QUEUE, "" + goodsId, "" + user.getUuid());
        // 2. 从 Redis Hash 移除
        redisService.hdel(WaitlistKeyPrefix.WL_DETAIL, "" + goodsId, "" + user.getUuid());
        // 3. DB中标记为已取消
        waitlistMapper.updateStatus(user.getUuid(), goodsId, 2);
        return true;
    }

    @Override
    public WaitlistStatusVo getWaitlistStatus(long userId, long goodsId) {
        WaitlistStatusVo statusVo = new WaitlistStatusVo();

        // 1. 从 Redis Hash 获取详情
        String detailJson = redisService.hget(WaitlistKeyPrefix.WL_DETAIL, "" + goodsId, "" + userId);
        if (detailJson != null) {
            Map<String, String> detail = JSON.parseObject(detailJson, Map.class);
            int status = Integer.parseInt(detail.get("status"));
            long ticketNo = Long.parseLong(detail.get("ticketNo"));

            statusVo.setStatus(status);
            statusVo.setTicketNo(ticketNo);

            if (status == 0) {
                // 排队中：获取排位
                Long rank = redisService.zrank(WaitlistKeyPrefix.WL_QUEUE, "" + goodsId, "" + userId);
                int position = (rank != null) ? (int) (rank + 1) : 0;
                long queueSize = redisService.zcard(WaitlistKeyPrefix.WL_QUEUE, "" + goodsId);
                statusVo.setPosition(position);
                statusVo.setQueueSize(queueSize);
            } else if (status == 1) {
                // 已转单：获取订单id
                SeckillWaitlist wl = waitlistMapper.getByUserIdAndGoodsId(userId, goodsId);
                if (wl != null && wl.getOrderId() != null) {
                    statusVo.setOrderId(wl.getOrderId());
                }
            }
            return statusVo;
        }

        // 2. Redis中没有，从DB获取
        SeckillWaitlist wl = waitlistMapper.getByUserIdAndGoodsId(userId, goodsId);
        if (wl == null) {
            statusVo.setStatus(-1);
            return statusVo;
        }

        statusVo.setStatus(wl.getStatus());
        if (wl.getOrderId() != null) {
            statusVo.setOrderId(wl.getOrderId());
        }
        return statusVo;
    }

    @Override
    public int processReplenishment(long goodsId, int quantity) {
        String lockKey = "waitlist:replenish:" + goodsId;
        String lockValue = UUID.randomUUID().toString();

        // 1. 获取分布式锁
        if (!dLock.lock(lockKey, lockValue, 30000)) {
            return 0;
        }

        try {
            // 2. 从DB获取排队中的候补列表
            List<SeckillWaitlist> waitingList = waitlistMapper.getWaitingListByGoodsId(goodsId, quantity);
            if (waitingList == null || waitingList.isEmpty()) {
                return 0;
            }

            int converted = 0;
            for (SeckillWaitlist wl : waitingList) {
                // 3. 检查用户是否仍在ZSET中（可能已取消）
                Double score = redisService.zscore(WaitlistKeyPrefix.WL_QUEUE, "" + goodsId, "" + wl.getUserId());
                if (score == null) {
                    // 已取消，更新DB状态
                    waitlistMapper.updateStatus(wl.getUserId(), goodsId, 2);
                    continue;
                }

                // 4. 检查是否已有秒杀订单（幂等）
                SeckillOrder existingOrder = orderService.getSeckillOrderByUserIdAndGoodsId(wl.getUserId(), goodsId);
                if (existingOrder != null) {
                    // 关联已有订单，清理Redis
                    waitlistMapper.updateStatusToOrdered(wl.getId(), existingOrder.getOrderId());
                    redisService.zrem(WaitlistKeyPrefix.WL_QUEUE, "" + goodsId, "" + wl.getUserId());
                    continue;
                }

                // 5. 减库存
                GoodsVo goods = goodsService.getGoodsVoByGoodsId(goodsId);
                boolean reduced = goodsService.reduceStock(goods);
                if (!reduced) {
                    break;
                }

                // 6. 创建订单
                UserVo user = new UserVo();
                user.setUuid(wl.getUserId());
                OrderInfo order = orderService.createOrder(user, goods);

                // 7. 更新候补状态为已转单
                waitlistMapper.updateStatusToOrdered(wl.getId(), order.getId());

                // 8. 清理Redis队列
                redisService.zrem(WaitlistKeyPrefix.WL_QUEUE, "" + goodsId, "" + wl.getUserId());

                converted++;
            }

            return converted;
        } finally {
            dLock.unlock(lockKey, lockValue);
        }
    }
}
