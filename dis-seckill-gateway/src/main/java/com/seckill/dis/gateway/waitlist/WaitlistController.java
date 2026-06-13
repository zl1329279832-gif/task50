package com.seckill.dis.gateway.waitlist;

import com.seckill.dis.common.api.user.vo.UserVo;
import com.seckill.dis.common.api.waitlist.WaitlistServiceApi;
import com.seckill.dis.common.api.waitlist.vo.WaitlistResultVo;
import com.seckill.dis.common.api.waitlist.vo.WaitlistStatusVo;
import com.seckill.dis.common.exception.GlobalException;
import com.seckill.dis.common.result.CodeMsg;
import com.seckill.dis.common.result.Result;
import com.seckill.dis.gateway.config.access.AccessLimit;
import org.apache.dubbo.config.annotation.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

/**
 * 候补抢购接口
 *
 * @author seckill
 */
@Controller
@RequestMapping("/waitlist/")
public class WaitlistController {

    private static final Logger logger = LoggerFactory.getLogger(WaitlistController.class);

    @Reference(interfaceClass = WaitlistServiceApi.class)
    WaitlistServiceApi waitlistService;

    /**
     * 加入候补抢购队列
     * 限流：10秒内最多3次
     *
     * @param user    当前登录用户
     * @param goodsId 商品ID
     * @return 候补结果（票据号、排位、过期时间）
     */
    @AccessLimit(seconds = 10, maxAccessCount = 3, needLogin = true)
    @RequestMapping(value = "join", method = RequestMethod.POST)
    @ResponseBody
    public Result<WaitlistResultVo> joinWaitlist(
            UserVo user,
            @RequestParam("goodsId") long goodsId) {

        if (user == null) {
            return Result.error(CodeMsg.SESSION_ERROR);
        }
        if (goodsId <= 0) {
            return Result.error(CodeMsg.SECKILL_PARM_ILLEGAL.fillArgs("商品id非法"));
        }

        try {
            WaitlistResultVo result = waitlistService.joinWaitlist(user, goodsId);
            return Result.success(result);
        } catch (GlobalException e) {
            return Result.error(e.getCodeMsg());
        } catch (Exception e) {
            logger.error("joinWaitlist error: userId={}, goodsId={}", user.getUuid(), goodsId, e);
            return Result.error(CodeMsg.WL_JOIN_FAIL);
        }
    }

    /**
     * 查询候补状态（客户端轮询）
     *
     * @param user    当前登录用户
     * @param goodsId 商品ID
     * @return 候补状态
     */
    @RequestMapping(value = "status", method = RequestMethod.GET)
    @ResponseBody
    public Result<WaitlistStatusVo> getWaitlistStatus(
            UserVo user,
            @RequestParam("goodsId") long goodsId) {

        if (user == null) {
            return Result.error(CodeMsg.SESSION_ERROR);
        }
        if (goodsId <= 0) {
            return Result.error(CodeMsg.SECKILL_PARM_ILLEGAL.fillArgs("商品id非法"));
        }

        try {
            WaitlistStatusVo status = waitlistService.getWaitlistStatus(user.getUuid(), goodsId);
            return Result.success(status);
        } catch (Exception e) {
            logger.error("getWaitlistStatus error: userId={}, goodsId={}", user.getUuid(), goodsId, e);
            return Result.error(CodeMsg.SERVER_ERROR);
        }
    }

    /**
     * 取消候补
     *
     * @param user    当前登录用户
     * @param goodsId 商品ID
     * @return 是否取消成功
     */
    @RequestMapping(value = "cancel", method = RequestMethod.POST)
    @ResponseBody
    public Result<Boolean> cancelWaitlist(
            UserVo user,
            @RequestParam("goodsId") long goodsId) {

        if (user == null) {
            return Result.error(CodeMsg.SESSION_ERROR);
        }
        if (goodsId <= 0) {
            return Result.error(CodeMsg.SECKILL_PARM_ILLEGAL.fillArgs("商品id非法"));
        }

        try {
            boolean cancelled = waitlistService.cancelWaitlist(user, goodsId);
            return Result.success(cancelled);
        } catch (Exception e) {
            logger.error("cancelWaitlist error: userId={}, goodsId={}", user.getUuid(), goodsId, e);
            return Result.error(CodeMsg.WL_CANCEL_FAIL);
        }
    }
}
