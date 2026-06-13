package com.seckill.dis.common.api.cache.vo;


import java.io.Serializable;

/**
 * 判断秒杀状态的key前缀
 */
public class SkKeyPrefix extends BaseKeyPrefix implements Serializable {
    public SkKeyPrefix(String prefix) {
        super(prefix);
    }

    public SkKeyPrefix(int expireSeconds, String prefix) {
        super(expireSeconds, prefix);
    }

    public static SkKeyPrefix isGoodsOver = new SkKeyPrefix("isGoodsOver");
    /**
     * 库存为0的商品的前缀
     */
    public static SkKeyPrefix GOODS_SK_OVER = new SkKeyPrefix("goodsSkOver");

    /**
     * 秒杀接口随机地址
     */
    public static SkKeyPrefix skPath = new SkKeyPrefix(60, "skPath");
    public static SkKeyPrefix SK_PATH = new SkKeyPrefix(60, "skPath");
    // 验证码5分钟有效
    public static SkKeyPrefix skVerifyCode = new SkKeyPrefix(300, "skVerifyCode");
    /**
     * 验证码5分钟有效
     */
    public static SkKeyPrefix VERIFY_RESULT = new SkKeyPrefix(300, "verifyResult");

    /**
     * 标记用户的秒杀请求已被 MQ 消费者处理（key: userId_goodsId, value: true）。
     * 用于区分"消息尚在队列排队"与"消息已消费但未生成订单（失败）"两种情形，
     * 从而让 getSeckillResult 在失败场景下能够返回 -1 而非永远返回 0。
     */
    public static SkKeyPrefix SK_PROCESSED = new SkKeyPrefix("skProcessed");
}
