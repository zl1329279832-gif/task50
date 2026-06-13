package com.seckill.dis.common.api.waitlist;

import com.seckill.dis.common.api.user.vo.UserVo;
import com.seckill.dis.common.api.waitlist.vo.WaitlistResultVo;
import com.seckill.dis.common.api.waitlist.vo.WaitlistStatusVo;

/**
 * 候补抢购服务接口
 *
 * @author seckill
 */
public interface WaitlistServiceApi {

    /**
     * 加入候补抢购队列
     *
     * @param user    用户
     * @param goodsId 商品ID
     * @return 候补结果（票据号、排位、过期时间）
     */
    WaitlistResultVo joinWaitlist(UserVo user, long goodsId);

    /**
     * 查询候补状态
     *
     * @param userId  用户ID
     * @param goodsId 商品ID
     * @return 候补状态（状态、排位、订单ID）
     */
    WaitlistStatusVo getWaitlistStatus(Long userId, long goodsId);

    /**
     * 取消候补
     *
     * @param user    用户
     * @param goodsId 商品ID
     * @return 是否取消成功
     */
    boolean cancelWaitlist(UserVo user, long goodsId);

    /**
     * 处理补库存后的候补转单（由MQ消费者调用）
     *
     * @param goodsId        商品ID
     * @param replenishCount 补充的库存数量
     * @return 实际转单数量
     */
    int processReplenishment(long goodsId, int replenishCount);
}
