package com.seckill.dis.common.api.waitlist.vo;

import java.io.Serializable;

/**
 * 候补状态查询结果
 *
 * @author seckill
 */
public class WaitlistStatusVo implements Serializable {

    /**
     * 状态:
     * -1: 不存在
     *  0: 排队中
     *  1: 已转单
     *  2: 已取消
     *  3: 已过期
     */
    private int status;

    /** 当前排位（仅status=0时有意义） */
    private int position;

    /** 转单后的订单ID（仅status=1时有意义） */
    private long orderId;

    /** 候补票据号 */
    private long ticketNo;

    /** 队列总人数 */
    private long queueSize;

    public WaitlistStatusVo() {
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public long getOrderId() {
        return orderId;
    }

    public void setOrderId(long orderId) {
        this.orderId = orderId;
    }

    public long getTicketNo() {
        return ticketNo;
    }

    public void setTicketNo(long ticketNo) {
        this.ticketNo = ticketNo;
    }

    public long getQueueSize() {
        return queueSize;
    }

    public void setQueueSize(long queueSize) {
        this.queueSize = queueSize;
    }

    @Override
    public String toString() {
        return "WaitlistStatusVo{" +
                "status=" + status +
                ", position=" + position +
                ", orderId=" + orderId +
                ", ticketNo=" + ticketNo +
                ", queueSize=" + queueSize +
                '}';
    }
}
