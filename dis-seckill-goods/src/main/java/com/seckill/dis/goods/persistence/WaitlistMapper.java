package com.seckill.dis.goods.persistence;

import com.seckill.dis.common.domain.SeckillWaitlist;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 候补抢购队列表数据访问层
 *
 * @author seckill
 */
@Mapper
public interface WaitlistMapper {

    /**
     * 插入候补记录
     *
     * @param waitlist 候补记录
     * @return 主键ID
     */
    @Insert("INSERT INTO seckill_waitlist (user_id, goods_id, status, position, create_date, expire_date) " +
            "VALUES (#{userId}, #{goodsId}, #{status}, #{position}, #{createDate}, #{expireDate})")
    @SelectKey(keyColumn = "id", keyProperty = "id", resultType = long.class,
            before = false, statement = "SELECT last_insert_id()")
    long insert(SeckillWaitlist waitlist);

    /**
     * 通过用户ID和商品ID查询候补记录
     *
     * @param userId  用户ID
     * @param goodsId 商品ID
     * @return 候补记录
     */
    @Select("SELECT * FROM seckill_waitlist WHERE user_id = #{userId} AND goods_id = #{goodsId}")
    SeckillWaitlist getByUserIdAndGoodsId(@Param("userId") long userId, @Param("goodsId") long goodsId);

    /**
     * 更新候补状态为已转单
     *
     * @param id      候补记录ID
     * @param orderId 订单ID
     * @return 影响行数
     */
    @Update("UPDATE seckill_waitlist SET status = 1, order_id = #{orderId}, " +
            "update_date = NOW() WHERE id = #{id} AND status = 0")
    int updateStatusToOrdered(@Param("id") long id, @Param("orderId") long orderId);

    /**
     * 更新候补状态
     *
     * @param userId  用户ID
     * @param goodsId 商品ID
     * @param status  新状态
     * @return 影响行数
     */
    @Update("UPDATE seckill_waitlist SET status = #{status}, update_date = NOW() " +
            "WHERE user_id = #{userId} AND goods_id = #{goodsId} AND status = 0")
    int updateStatus(@Param("userId") long userId, @Param("goodsId") long goodsId,
                     @Param("status") int status);

    /**
     * 获取指定商品的候补列表（按加入时间升序），用于补库存转单
     *
     * @param goodsId 商品ID
     * @param limit   最大数量
     * @return 候补记录列表
     */
    @Select("SELECT * FROM seckill_waitlist WHERE goods_id = #{goodsId} AND status = 0 " +
            "ORDER BY create_date ASC LIMIT #{limit}")
    List<SeckillWaitlist> getWaitingListByGoodsId(@Param("goodsId") long goodsId,
                                                   @Param("limit") int limit);

    /**
     * 统计指定商品的排队人数
     *
     * @param goodsId 商品ID
     * @return 排队人数
     */
    @Select("SELECT COUNT(*) FROM seckill_waitlist WHERE goods_id = #{goodsId} AND status = 0")
    int countWaiting(@Param("goodsId") long goodsId);
}
