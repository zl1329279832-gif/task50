package com.seckill.dis.common.api.waitlist.vo;

import java.io.Serializable;

/**
 * 候补状态查询结果
 */
public class WaitlistStatusVo implements Serializable {

    /**
     * 0=排队中, 1=已转单, 2=已取消, -1=不存在
     */
    private int status;
    private int position;
    private long queueSize;
    private long ticketNo;
    private long orderId;

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

    public long getQueueSize() {
        return queueSize;
    }

    public void setQueueSize(long queueSize) {
        this.queueSize = queueSize;
    }

    public long getTicketNo() {
        return ticketNo;
    }

    public void setTicketNo(long ticketNo) {
        this.ticketNo = ticketNo;
    }

    public long getOrderId() {
        return orderId;
    }

    public void setOrderId(long orderId) {
        this.orderId = orderId;
    }
}
