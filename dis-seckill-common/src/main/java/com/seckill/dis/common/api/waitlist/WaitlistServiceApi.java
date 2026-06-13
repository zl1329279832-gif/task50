package com.seckill.dis.common.api.waitlist;

import com.seckill.dis.common.api.user.vo.UserVo;
import com.seckill.dis.common.api.waitlist.vo.WaitlistResultVo;
import com.seckill.dis.common.api.waitlist.vo.WaitlistStatusVo;

/**
 * 候补抢购服务接口
 */
public interface WaitlistServiceApi {

    /**
     * 加入候补队列
     *
     * @param user    用户
     * @param goodsId 商品id
     * @return 候补结果（候补单号、排位、过期时间）
     */
    WaitlistResultVo joinWaitlist(UserVo user, long goodsId);

    /**
     * 取消候补
     *
     * @param user    用户
     * @param goodsId 商品id
     * @return 是否成功
     */
    boolean cancelWaitlist(UserVo user, long goodsId);

    /**
     * 查询候补状态
     *
     * @param userId  用户id
     * @param goodsId 商品id
     * @return 候补状态
     */
    WaitlistStatusVo getWaitlistStatus(long userId, long goodsId);

    /**
     * 补库存转单处理
     *
     * @param goodsId  商品id
     * @param quantity 补充数量
     * @return 成功转单数量
     */
    int processReplenishment(long goodsId, int quantity);
}
