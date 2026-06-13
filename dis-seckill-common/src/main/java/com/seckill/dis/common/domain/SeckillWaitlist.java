package com.seckill.dis.common.domain;

import java.io.Serializable;
import java.util.Date;

/**
 * seckill_waitlist 候补表
 */
public class SeckillWaitlist implements Serializable {

    private Long id;
    private Long userId;
    private Long goodsId;
    /**
     * 0=排队中, 1=已转单, 2=已取消
     */
    private Integer status;
    private Long orderId;
    private Date createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getGoodsId() {
        return goodsId;
    }

    public void setGoodsId(Long goodsId) {
        this.goodsId = goodsId;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }
}
