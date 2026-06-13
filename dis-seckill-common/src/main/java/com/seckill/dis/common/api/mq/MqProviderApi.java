package com.seckill.dis.common.api.mq;

import com.seckill.dis.common.api.mq.vo.ReplenishMessage;
import com.seckill.dis.common.api.mq.vo.SkMessage;

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
     * 将补库存消息投递到MQ中
     *
     * @param message
     */
    void sendReplenishMessage(ReplenishMessage message);
}
