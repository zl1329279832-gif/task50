package com.seckill.dis.common.api.mq.vo;

import java.io.Serializable;

/**
 * 补库存消息
 */
public class ReplenishMessage implements Serializable {

    private long goodsId;
    private int quantity;

    public long getGoodsId() {
        return goodsId;
    }

    public void setGoodsId(long goodsId) {
        this.goodsId = goodsId;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }
}
