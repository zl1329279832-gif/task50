package com.seckill.dis.common.api.cache.vo;

import java.io.Serializable;

/**
 * 候补队列 Redis key 前缀
 */
public class WaitlistKeyPrefix extends BaseKeyPrefix implements Serializable {

    public WaitlistKeyPrefix(String prefix) {
        super(prefix);
    }

    public WaitlistKeyPrefix(int expireSeconds, String prefix) {
        super(expireSeconds, prefix);
    }

    /**
     * 候补排队 ZSET（score=时间戳，member=userId）
     */
    public static WaitlistKeyPrefix WL_QUEUE = new WaitlistKeyPrefix("wlQueue");

    /**
     * 候补详情 HASH（field=userId，value=json detail）
     */
    public static WaitlistKeyPrefix WL_DETAIL = new WaitlistKeyPrefix("wlDetail");
}
