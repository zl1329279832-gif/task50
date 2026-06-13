package com.seckill.dis.common.api.waitlist.vo;

import java.io.Serializable;
import java.util.Date;

/**
 * 加入候补队列的返回结果
 *
 * @author seckill
 */
public class WaitlistResultVo implements Serializable {

    /** 候补票据号（= waitlist表的主键id） */
    private long ticketNo;

    /** 当前排位（从1开始） */
    private int position;

    /** 候补过期时间 */
    private Date expireTime;

    /** 队列总人数 */
    private long queueSize;

    public WaitlistResultVo() {
    }

    public WaitlistResultVo(long ticketNo, int position, Date expireTime, long queueSize) {
        this.ticketNo = ticketNo;
        this.position = position;
        this.expireTime = expireTime;
        this.queueSize = queueSize;
    }

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

    public Date getExpireTime() {
        return expireTime;
    }

    public void setExpireTime(Date expireTime) {
        this.expireTime = expireTime;
    }

    public long getQueueSize() {
        return queueSize;
    }

    public void setQueueSize(long queueSize) {
        this.queueSize = queueSize;
    }

    @Override
    public String toString() {
        return "WaitlistResultVo{" +
                "ticketNo=" + ticketNo +
                ", position=" + position +
                ", expireTime=" + expireTime +
                ", queueSize=" + queueSize +
                '}';
    }
}
