package com.seckill.dis.mq.receiver;

import com.seckill.dis.common.api.waitlist.WaitlistServiceApi;
import com.seckill.dis.common.api.waitlist.vo.ReplenishMessage;
import com.seckill.dis.mq.config.MQConfig;
import org.apache.dubbo.config.annotation.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

/**
 * 候补补库存消息消费者
 * 收到补库存消息后，异步处理候补队列，按排位生成订单
 *
 * @author seckill
 */
@Service
public class WaitlistReplenishConsumer {

    private static final Logger logger = LoggerFactory.getLogger(WaitlistReplenishConsumer.class);

    @Reference(interfaceClass = WaitlistServiceApi.class)
    WaitlistServiceApi waitlistService;

    /**
     * 处理补库存消息，触发候补转单
     *
     * @param message 补库存消息
     */
    @RabbitListener(queues = MQConfig.WAITLIST_REPLENISH_QUEUE)
    public void receiveReplenishMessage(ReplenishMessage message) {
        logger.info("MQ receive replenish message: goodsId={}, count={}",
                message.getGoodsId(), message.getReplenishCount());

        try {
            int converted = waitlistService.processReplenishment(
                    message.getGoodsId(), message.getReplenishCount());
            logger.info("Replenishment processing done: goodsId={}, converted={}",
                    message.getGoodsId(), converted);
        } catch (Exception e) {
            logger.error("Replenishment processing failed: goodsId={}", message.getGoodsId(), e);
        }
    }
}
