# Redis 中存储的数据

> 本文档列出系统运行时 Redis 中的所有 key，按功能分组。
> key 格式遵循 `类名:前缀{动态部分}` 规则（由 `BaseKeyPrefix.getPrefix()` 拼接参数生成）。

---

## 一、用户与会话

```properties
# 1. 根据手机号缓存的用户信息（对象级缓存，减少 DB 查询）
key:    SkUserKeyPrefix:id_{phone}
type:   STRING
value:  {SeckillUser JSON}
expire: 0（永不过期）
写入方: UserServiceImpl.getByPhone()
读取方: UserServiceImpl.getByPhone(), AccessInterceptor

# 2. 用户登录 token → 用户信息（会话鉴权）
key:    SkUserKeyPrefix:token{token}
type:   STRING
value:  {SeckillUser JSON}
expire: 1800s（30 分钟）
写入方: UserServiceImpl.login(), AccessInterceptor.addCookie()
读取方: AccessInterceptor.getUser(), UserArgumentResolver
```

## 二、商品与库存

```properties
# 3. 商品列表 HTML 页面缓存
key:    GoodsKeyPrefix:goodsListHtml
type:   STRING
value:  {html}
expire: 60s（1 分钟）
写入方: GoodsController.goodsList()
读取方: GoodsController.goodsList()

# 4. 商品库存预加载（系统启动时从 DB 加载，秒杀时通过 decr 预减）
key:    GoodsKeyPrefix:goodsStock{goodsId}
type:   STRING
value:  {stock 数量}
expire: 0（永不过期）
写入方: SeckillController.afterPropertiesSet(),
        SeckillServiceImpl.seckill()（秒杀成功后刷新）,
        WaitlistServiceImpl.processReplenishment()（补货后刷新）
读取方: SeckillController.doSeckill()（通过 decr 预减库存）
```

## 三、秒杀流程控制

```properties
# 5. 验证码计算结果
key:    SkKeyPrefix:verifyResult{uuid}_{goodsId}
type:   STRING
value:  {int 验证码计算结果}
expire: 300s（5 分钟）
写入方: SeckillController.getVerifyCode()
读取方: SeckillController.checkVerifyCode()
删除方: SeckillController.checkVerifyCode()（校验后立即删除）

# 6. 随机秒杀路径（防止接口地址被猜测）
key:    SkKeyPrefix:skPath{userId}_{goodsId}
type:   STRING
value:  {UUID 随机路径}
expire: 60s（1 分钟）
写入方: SeckillController.createSkPath()
读取方: SeckillController.checkPath()
删除方: SeckillController.getVerifyCode()（重新获取验证码时清除旧路径）

# 7. 商品售罄标记（库存减为 0 时写入）
#    ★ Gateway 启动时据此恢复本地 localOverMap，轮询结果时据此判定秒杀失败
key:    SkKeyPrefix:goodsSkOver{goodsId}
type:   STRING
value:  true
expire: 0（永不过期）
写入方: SeckillServiceImpl.setGoodsOver()（reduceStock 失败时）
读取方: SeckillController.afterPropertiesSet()（启动恢复 localOverMap）,
        SeckillController.doSeckill()（localOverMap 未命中时的降级检查）,
        SeckillServiceImpl.getSeckillResult()（轮询结果判定）
删除方: SeckillServiceImpl.seckill()（秒杀成功且库存 > 0 时清除）,
        WaitlistServiceImpl.processReplenishment()（补货后库存 > 0 时清除）

# 8. MQ 消费完成标记（防止前端轮询永远返回"排队中"）
#    ★ MQ 消费者处理完消息后写入，无论成功/失败/重复都会设置
key:    SkKeyPrefix:skProcessed{userId}_{goodsId}
type:   STRING
value:  true
expire: 0（永不过期）
写入方: SeckillServiceImpl.setSeckillProcessed()（由 MqConsumer 在每条消息处理完后调用）
读取方: SeckillServiceImpl.getSeckillResult()（轮询时检查：已处理但无订单 → 返回 -1 失败）
```

## 四、订单

```properties
# 9. 秒杀订单缓存（防止重复秒杀 + 快速查询）
key:    OrderKeyPrefix:SK_ORDER:{userId}_{goodsId}
type:   STRING
value:  {SeckillOrder JSON}
expire: 0（永不过期）
写入方: OrderServiceImpl.createOrder()
读取方: SeckillController.doSeckill()（判断是否已秒杀）,
        MqConsumer（消费前检查重复订单）
```

## 五、接口防刷

```properties
# 10. 用户访问频率计数器
key:    AccessKeyPrefix:access{URI}_{phone}
type:   STRING
value:  {int 访问次数}
expire: @AccessLimit#seconds（注解配置的时间窗口）
写入方: AccessInterceptor.preHandle()
读取方: AccessInterceptor.preHandle()
```

## 六、候补排队（Waitlist）

```properties
# 11. 候补排队队列（有序集合，score = 加入时间戳，实现 FIFO）
key:    WaitlistKeyPrefix:wlQueue{goodsId}
type:   ZSET
value:  member = userId, score = joinTime(timestamp)
expire: 0（永不过期）
写入方: WaitlistServiceImpl.joinWaitlist()（ZADD）
读取方: WaitlistServiceImpl（ZSCORE 判断是否在队列中, ZRANK 查排名, ZCARD 查队列长度）
删除方: WaitlistServiceImpl.cancelWaitlist()（ZREM）,
        WaitlistServiceImpl.processReplenishment()（转单成功后 ZREM）

# 12. 候补详情（哈希表，存储每个用户的候补状态）
key:    WaitlistKeyPrefix:wlDetail{goodsId}
type:   HASH
value:  field = userId,
        value = JSON{status, joinTime, position, ticketNo, orderId}
expire: 0（永不过期）
写入方: WaitlistServiceImpl.joinWaitlist()（HSET, status=WAITING）,
        WaitlistServiceImpl.processReplenishment()（HSET, status=CONVERTED, 写入 orderId）
读取方: WaitlistServiceImpl.getWaitlistStatus()（HGET）
删除方: WaitlistServiceImpl.cancelWaitlist()（HDEL）

# 13. 候补补货分布式锁（防止多实例并发处理同一商品的补货）
key:    WaitlistKeyPrefix:wlRepLock{goodsId}
type:   STRING
value:  {lockValue}
expire: 30s
写入方: WaitlistServiceImpl.processReplenishment()（dLock.lock）
删除方: WaitlistServiceImpl.processReplenishment()（dLock.unlock）
```

## 七、分布式锁（非 KeyPrefix 体系，直接拼接 key）

```properties
# 14. 秒杀请求分布式锁（防止同一用户并发发送多条 MQ 消息）
key:    sk_lock:{userId}_{goodsId}
type:   STRING
value:  {lockValue}
expire: 5s
写入方: SeckillController.doSeckill()（dLock.lock）
删除方: SeckillController.doSeckill()（dLock.unlock）

# 15. 用户注册分布式锁（防止恶意用户重复注册）
key:    redis-lock{phone}
type:   STRING
value:  {lockValue}
expire: 60s
写入方: UserServiceImpl.register()（dLock.lock）
删除方: UserServiceImpl.register()（dLock.unlock）
```

---

## 附：旧版字段说明

以下字段定义在代码中但当前未被业务逻辑引用，属于历史遗留：

| 字段 | 类 | 前缀 | 说明 |
|------|----|------|------|
| `isGoodsOver` | SkKeyPrefix | `"isGoodsOver"` | 已被 `GOODS_SK_OVER`（`"goodsSkOver"`）替代 |
| `skVerifyCode` | SkKeyPrefix | `"skVerifyCode"` | 已被 `VERIFY_RESULT`（`"verifyResult"`）替代 |
| `skPath` | SkKeyPrefix | `"skPath"` | 与 `SK_PATH` 同名同义，为旧版别名 |
| `goodsListKeyPrefix` | GoodsKeyPrefix | `"goodsList"` | 已被 `GOODS_LIST_HTML`（`"goodsListHtml"`）替代 |
| `seckillGoodsStockPrefix` | GoodsKeyPrefix | `"goodsStock"` | 与 `GOODS_STOCK` 同名同义，为旧版别名 |
| `getSeckillOrderByUidGid` | OrderKeyPrefix | `"getSeckillOrderByUidGid"` | 已被 `SK_ORDER` 替代 |
