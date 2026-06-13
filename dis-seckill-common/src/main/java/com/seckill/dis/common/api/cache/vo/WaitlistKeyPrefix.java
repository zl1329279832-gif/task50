package com.seckill.dis.common.api.cache.vo;

import java.io.Serializable;

/**
 * 候补抢购相关的 Redis key 前缀
 *
 * @author seckill
 */
public class WaitlistKeyPrefix extends BaseKeyPrefix implements Serializable {

    public WaitlistKeyPrefix(String prefix) {
        super(prefix);
    }

    public WaitlistKeyPrefix(int expireSeconds, String prefix) {
        super(expireSeconds, prefix);
    }

    /**
     * ZSET: 候补队列
     * key = WaitlistKeyPrefix:wlQueue + goodsId
     * member = userId, score = timestamp（FIFO 排序）
     * 不过期——由业务逻辑控制清理
     */
    public static WaitlistKeyPrefix WL_QUEUE = new WaitlistKeyPrefix("wlQueue");

    /**
     * HASH: 候补详情
     * key = WaitlistKeyPrefix:wlDetail + goodsId
     * field = userId, value = JSON{status, joinTime, position, ticketNo}
     * 不过期——由业务逻辑控制清理
     */
    public static WaitlistKeyPrefix WL_DETAIL = new WaitlistKeyPrefix("wlDetail");

    /**
     * STRING: 补库存处理分布式锁
     * key = WaitlistKeyPrefix:wlRepLock + goodsId
     * 30秒过期
     */
    public static WaitlistKeyPrefix WL_REPLENISH_LOCK = new WaitlistKeyPrefix(30, "wlRepLock");
}
