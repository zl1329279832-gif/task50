package com.seckill.dis.common.api.cache;

import com.seckill.dis.common.api.cache.vo.KeyPrefix;

import java.util.List;
import java.util.Map;

/**
 * redis 服务接口
 *
 * @author noodle
 */
public interface RedisServiceApi {


    /**
     * redis 的get操作，通过key获取存储在redis中的对象
     *
     * @param prefix key的前缀
     * @param key    业务层传入的key
     * @param clazz  存储在redis中的对象类型（redis中是以字符串存储的）
     * @param <T>    指定对象对应的类型
     * @return 存储于redis中的对象
     */
    <T> T get(KeyPrefix prefix, String key, Class<T> clazz);

    /**
     * redis的set操作
     *
     * @param prefix 键的前缀
     * @param key    键
     * @param value  值
     * @return 操作成功为true，否则为true
     */
    <T> boolean set(KeyPrefix prefix, String key, T value);

    /**
     * 判断key是否存在于redis中
     *
     * @param keyPrefix key的前缀
     * @param key
     * @return
     */
    boolean exists(KeyPrefix keyPrefix, String key);

    /**
     * 自增
     *
     * @param keyPrefix
     * @param key
     * @return
     */
    long incr(KeyPrefix keyPrefix, String key);

    /**
     * 自减
     *
     * @param keyPrefix
     * @param key
     * @return
     */
    long decr(KeyPrefix keyPrefix, String key);


    /**
     * 删除缓存中的用户数据
     *
     * @param prefix
     * @param key
     * @return
     */
    boolean delete(KeyPrefix prefix, String key);

    // ── ZSET operations ──

    /** ZADD: 添加成员到有序集合 */
    boolean zadd(KeyPrefix prefix, String key, double score, String member);

    /** ZREM: 从有序集合中移除成员 */
    boolean zrem(KeyPrefix prefix, String key, String member);

    /** ZCARD: 获取有序集合的成员数量 */
    long zcard(KeyPrefix prefix, String key);

    /** ZSCORE: 获取成员的分数（不存在返回null） */
    Double zscore(KeyPrefix prefix, String key, String member);

    /** ZRANK: 获取成员的排名（0-based，不存在返回null） */
    Long zrank(KeyPrefix prefix, String key, String member);

    /** ZRANGE: 获取指定范围的成员列表 */
    List<String> zrange(KeyPrefix prefix, String key, long start, long stop);

    // ── Hash operations ──

    /** HSET: 设置哈希表字段 */
    boolean hset(KeyPrefix prefix, String key, String field, String value);

    /** HGET: 获取哈希表字段值 */
    String hget(KeyPrefix prefix, String key, String field);

    /** HDEL: 删除哈希表字段 */
    boolean hdel(KeyPrefix prefix, String key, String field);

    /** HGETALL: 获取哈希表所有字段和值 */
    Map<String, String> hgetAll(KeyPrefix prefix, String key);
}
