package com.seckill.dis.common.domain;

import java.io.Serializable;
import java.util.Date;

/**
 * 秒杀候补抢购队列表
 *
 * @author seckill
 */
public class SeckillWaitlist implements Serializable {

    private Long id;
    private Long userId;
    private Long goodsId;
    /** 状态: 0-排队中, 1-已转单, 2-已取消, 3-已过期 */
    private Integer status;
    private Integer position;
    private Long orderId;
    private Date createDate;
    private Date updateDate;
    private Date expireDate;

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

    public Integer getPosition() {
        return position;
    }

    public void setPosition(Integer position) {
        this.position = position;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public Date getCreateDate() {
        return createDate;
    }

    public void setCreateDate(Date createDate) {
        this.createDate = createDate;
    }

    public Date getUpdateDate() {
        return updateDate;
    }

    public void setUpdateDate(Date updateDate) {
        this.updateDate = updateDate;
    }

    public Date getExpireDate() {
        return expireDate;
    }

    public void setExpireDate(Date expireDate) {
        this.expireDate = expireDate;
    }

    @Override
    public String toString() {
        return "SeckillWaitlist{" +
                "id=" + id +
                ", userId=" + userId +
                ", goodsId=" + goodsId +
                ", status=" + status +
                ", position=" + position +
                ", orderId=" + orderId +
                ", createDate=" + createDate +
                ", updateDate=" + updateDate +
                ", expireDate=" + expireDate +
                '}';
    }
}
