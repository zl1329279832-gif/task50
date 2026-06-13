package com.seckill.dis.goods.persistence;

import com.seckill.dis.common.domain.SeckillWaitlist;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * seckill_waitlist 候补表数据库访问层
 */
@Mapper
public interface WaitlistMapper {

    /**
     * 查询用户对某商品的有效候补记录（排队中或已转单）
     */
    @Select("SELECT id, user_id AS userId, goods_id AS goodsId, status, order_id AS orderId, create_time AS createTime " +
            "FROM seckill_waitlist WHERE user_id=#{userId} AND goods_id=#{goodsId} AND status IN (0, 1, 2) " +
            "ORDER BY id DESC LIMIT 1")
    SeckillWaitlist getByUserIdAndGoodsId(@Param("userId") long userId, @Param("goodsId") long goodsId);

    /**
     * 插入候补记录，返回自增主键
     */
    @Insert("INSERT INTO seckill_waitlist(user_id, goods_id, status, create_time) " +
            "VALUES(#{userId}, #{goodsId}, #{status}, #{createTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    long insert(SeckillWaitlist waitlist);

    /**
     * 更新候补状态
     */
    @Update("UPDATE seckill_waitlist SET status=#{status} WHERE user_id=#{userId} AND goods_id=#{goodsId}")
    int updateStatus(@Param("userId") long userId, @Param("goodsId") long goodsId, @Param("status") int status);

    /**
     * 查询排队中的候补列表（按id排序保证FIFO）
     */
    @Select("SELECT id, user_id AS userId, goods_id AS goodsId, status, order_id AS orderId, create_time AS createTime " +
            "FROM seckill_waitlist WHERE goods_id=#{goodsId} AND status=0 ORDER BY id ASC LIMIT #{limit}")
    List<SeckillWaitlist> getWaitingListByGoodsId(@Param("goodsId") long goodsId, @Param("limit") int limit);

    /**
     * 将候补状态更新为已转单，并关联订单id
     */
    @Update("UPDATE seckill_waitlist SET status=1, order_id=#{orderId} WHERE id=#{id}")
    int updateStatusToOrdered(@Param("id") long id, @Param("orderId") long orderId);
}
