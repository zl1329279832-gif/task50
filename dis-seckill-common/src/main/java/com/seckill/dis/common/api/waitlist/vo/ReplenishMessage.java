package com.seckill.dis.common.api.waitlist.vo;

import java.io.Serializable;

/**
 * 补库存消息，用于触发候补转单
 *
 * @author seckill
 */
public class ReplenishMessage implements Serializable {

    private long goodsId;

    /** 本次补充的库存数量 */
    private int replenishCount;

    public ReplenishMessage() {
    }

    public ReplenishMessage(long goodsId, int replenishCount) {
        this.goodsId = goodsId;
        this.replenishCount = replenishCount;
    }

    public long getGoodsId() {
        return goodsId;
    }

    public void setGoodsId(long goodsId) {
        this.goodsId = goodsId;
    }

    public int getReplenishCount() {
        return replenishCount;
    }

    public void setReplenishCount(int replenishCount) {
        this.replenishCount = replenishCount;
    }

    @Override
    public String toString() {
        return "ReplenishMessage{" +
                "goodsId=" + goodsId +
                ", replenishCount=" + replenishCount +
                '}';
    }
}
