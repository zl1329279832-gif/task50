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
     * 秒杀消息已消费标记（per user per goods），用于区分"排队中"和"已处理但无订单"
     */
    public static SkKeyPrefix SK_PROCESSED = new SkKeyPrefix("skProcessed");
}
