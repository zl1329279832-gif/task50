package com.seckill.dis.common.api.waitlist.vo;

import java.io.Serializable;
import java.util.Date;

/**
 * 加入候补队列返回结果
 */
public class WaitlistResultVo implements Serializable {

    private long ticketNo;
    private int position;
    private long queueSize;
    private Date expireTime;

    public long getTicketNo() {
        return ticketNo;
    }

    public void setTicketNo(long ticketNo) {
        this.ticketNo = ticketNo;
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

    public Date getExpireTime() {
        return expireTime;
    }

    public void setExpireTime(Date expireTime) {
        this.expireTime = expireTime;
    }
}
