package com.seckill.dis.common.api.mq;

import com.seckill.dis.common.api.mq.vo.SkMessage;
import com.seckill.dis.common.api.waitlist.vo.ReplenishMessage;

/**
 * 消息队列服务
 *
 * @author noodle
 */
public interface MqProviderApi {

    /**
     * 将用户秒杀信息投递到MQ中（使用direct模式的exchange）
     *
     * @param message
     */
    void sendSkMessage(SkMessage message);

    /**
     * 发送补库存消息（触发候补转单）
     *
     * @param message 补库存消息
     */
    void sendReplenishMessage(ReplenishMessage message);
}
