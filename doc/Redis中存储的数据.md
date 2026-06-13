# Redis 中存储的数据

> **阅读说明**
>
> 1. 所有 Redis key 由 `BaseKeyPrefix.getPrefix()` 拼接而成，格式为 `类名:prefix常量值`，再拼上业务 key 后缀。
> 2. 表中 `{xxx}` 为运行时动态替换的变量，**实际 key 中不含花括号**。例如 `{goodsId}` 为 `1` 时，`SkKeyPrefix:goodsSkOver{goodsId}` 实际为 `SkKeyPrefix:goodsSkOver1`。
> 3. `expire: 0` 表示不过期（由业务逻辑主动清理）。

---

## 一、用户模块（SkUserKeyPrefix）

| # | Redis Key | Value | Expire | 说明 |
|---|-----------|-------|--------|------|
| 1 | `SkUserKeyPrefix:id{phone}` | SeckillUser（JSON） | 0 | 通过手机号缓存用户信息，用于快速查询 |
| 2 | `SkUserKeyPrefix:token{token}` | SeckillUser（JSON） | 30 min | 通过 token 缓存用户信息，用于鉴权；每次访问会刷新 TTL |

## 二、商品模块（GoodsKeyPrefix）

| # | Redis Key | Value | Expire | 说明 |
|---|-----------|-------|--------|------|
| 3 | `GoodsKeyPrefix:goodsListHtml` | HTML 字符串 | 60 s | 商品列表页的 Thymeleaf 渲染结果缓存 |
| 4 | `GoodsKeyPrefix:goodsStock{goodsId}` | 库存数量（int） | 0 | 系统启动时从 DB 预加载；`doSeckill` 中通过 `DECR` 预减库存 |

## 三、秒杀模块（SkKeyPrefix）

| # | Redis Key | Value | Expire | 说明 |
|---|-----------|-------|--------|------|
| 5 | `SkKeyPrefix:verifyResult{uuid}_{goodsId}` | 验证码计算结果（int） | 300 s (5 min) | 验证码校验通过后会被 `DELETE`，防止同一验证码重复提交 |
| 6 | `SkKeyPrefix:skPath{uuid}_{goodsId}` | 随机秒杀地址（String） | 60 s | 获取秒杀接口地址时写入；`doSeckill` 校验后消费；刷新验证码时主动删除 |
| 7 | `SkKeyPrefix:goodsSkOver{goodsId}` | `true` | 0 | **商品售罄标记**。DB 减库存失败时写入；补库存后若 `stockCount > 0` 则删除。**与 Gateway 本地 `localOverMap` 配合使用**（详见下方说明） |
| 8 | `SkKeyPrefix:skProcessed{userId}_{goodsId}` | `true` | 0 | **MQ 消息已消费标记**。MqConsumer 处理完秒杀消息后写入（无论成功或失败）。`getSeckillResult` 据此区分「排队中」和「已处理但无订单」 |

> **`isGoodsOver` 与 `GOODS_SK_OVER`**：代码中存在 `SkKeyPrefix.isGoodsOver`（prefix = `isGoodsOver`），但实际业务代码**仅使用** `GOODS_SK_OVER`（prefix = `goodsSkOver`）。`isGoodsOver` 为遗留常量，不建议在新逻辑中使用。

### localOverMap 与 GOODS_SK_OVER 的关系

Gateway 的 `SeckillController` 维护了一个 JVM 内存标记 `localOverMap`（`ConcurrentHashMap<Long, Boolean>`），与 Redis key `GOODS_SK_OVER` 配合工作：

```
┌─────────────────────────────────────────────────────────────────┐
│                    Gateway JVM 内存                              │
│                                                                 │
│  localOverMap: { goodsId → true/false }                         │
│  ├─ 启动时：从 Redis GOODS_SK_OVER 恢复（afterPropertiesSet）    │
│  ├─ 预减库存 < 0 时：localOverMap.put(goodsId, true)            │
│  └─ 作用：秒杀已结束时直接拦截请求，减少 Redis 访问               │
└─────────────────────────┬───────────────────────────────────────┘
                          │ 读取/写入
┌─────────────────────────▼───────────────────────────────────────┐
│                    Redis                                        │
│                                                                 │
│  SkKeyPrefix:goodsSkOver{goodsId} = true                        │
│  ├─ 写入时机：SeckillServiceImpl.seckill() → reduceStock 失败    │
│  ├─ 读取时机：doSeckill 中兜底检查（服务重启 localOverMap 为空时） │
│  └─ 删除时机：补库存后 stockCount > 0 时删除                     │
└─────────────────────────────────────────────────────────────────┘
```

**协作流程：**

1. **正常售罄**：Redis `DECR` 库存 → `< 0` → `localOverMap.put(goodsId, true)` 拦截后续请求。若后续 MQ 消费时 DB `reduceStock` 也失败 → `setGoodsOver()` 写入 Redis `GOODS_SK_OVER`。
2. **Gateway 重启**：`localOverMap` 被清空 → `afterPropertiesSet()` 从 Redis `GOODS_SK_OVER` 逐商品恢复售罄标记。
3. **补库存**：`processReplenishment` 完成后若 `stockCount > 0`，删除 Redis `GOODS_SK_OVER`。但 `localOverMap` 需等下次 `afterPropertiesSet` 或下一次 `doSeckill` 中 `DECR` 成功后才会自然失效（`localOverMap` 仅在 `< 0` 时写 `true`，不会主动清除）。

## 四、订单模块（OrderKeyPrefix）

| # | Redis Key | Value | Expire | 说明 |
|---|-----------|-------|--------|------|
| 9 | `OrderKeyPrefix:SK_ORDER:{userId}_{goodsId}` | SeckillOrder（JSON） | 0 | 秒杀订单缓存。MQ 消费端创建订单后写入；`doSeckill` 和 MqConsumer 先查 Redis 再查 DB，用于幂等判断 |

> 注意：代码中 `set` 时 key 参数为 `":" + userId + "_" + goodsId`，因此实际 Redis key 中 `SK_ORDER` 后有一个额外的冒号，如 `OrderKeyPrefix:SK_ORDER:123_456`。

## 五、接口防刷模块（AccessKeyPrefix）

| # | Redis Key | Value | Expire | 说明 |
|---|-----------|-------|--------|------|
| 10 | `AccessKeyPrefix:access{URI}_{phone}` | 访问次数（int） | `@AccessLimit#seconds` | 接口限流计数器，由 `AccessInterceptor` 在拦截器中使用 `INCR` 递增 |

## 六、候补抢购模块（WaitlistKeyPrefix）

| # | Redis Key | 数据结构 | Value | Expire | 说明 |
|---|-----------|---------|-------|--------|------|
| 11 | `WaitlistKeyPrefix:wlQueue{goodsId}` | **ZSET** | member = userId, score = 加入时间戳 | 0（业务清理） | 候补排队队列，按 score（时间戳）升序排列实现 FIFO。`zadd` 加入、`zrem` 取消/转单后移除、`zrank` 查排位、`zscore` 判断是否在队列中 |
| 12 | `WaitlistKeyPrefix:wlDetail{goodsId}` | **HASH** | field = userId, value = JSON `{status, joinTime, position, ticketNo}` | 0（业务清理） | 候补详情缓存。`status`: 0=排队中, 1=已转单, 2=已取消。转单后更新 `status` 和 `orderId` |
| 13 | `WaitlistKeyPrefix:wlRepLock{goodsId}` | **STRING** | 锁持有者 UUID | 30 s | 补库存处理的分布式锁 key（由 `DLockApi.lock/unlock` 操作），保证同一商品同一时刻只有一个线程执行 `processReplenishment` |

> **候补相关 key 的生命周期**：ZSET 和 HASH 不设 TTL，由以下业务动作清理：
> - **用户主动取消** → `zrem` + `hdel`
> - **候补转单成功** → `zrem` + `hset(status=1)`
> - **用户已取消但 DB 仍有记录** → `processReplenishment` 遍历时发现 `zscore == null`，更新 DB 状态为已取消

## 七、通用用户缓存（UserKey）

| # | Redis Key | Value | Expire | 说明 |
|---|-----------|-------|--------|------|
| 14 | `UserKey:id{id}` | User（JSON） | 0 | 通用用户表缓存（按 ID） |
| 15 | `UserKey:name{name}` | User（JSON） | 0 | 通用用户表缓存（按用户名） |

---

## 附：Redis Key 速查表

| 类名 | 常量名 | prefix 值 | 数据结构 | 典型过期时间 |
|------|--------|----------|---------|-------------|
| SkUserKeyPrefix | SK_USER_PHONE | `id` | STRING | 0 |
| SkUserKeyPrefix | TOKEN | `token` | STRING | 1800 s |
| GoodsKeyPrefix | GOODS_LIST_HTML | `goodsListHtml` | STRING | 60 s |
| GoodsKeyPrefix | GOODS_STOCK | `goodsStock` | STRING | 0 |
| SkKeyPrefix | VERIFY_RESULT | `verifyResult` | STRING | 300 s |
| SkKeyPrefix | SK_PATH | `skPath` | STRING | 60 s |
| SkKeyPrefix | GOODS_SK_OVER | `goodsSkOver` | STRING | 0 |
| SkKeyPrefix | SK_PROCESSED | `skProcessed` | STRING | 0 |
| OrderKeyPrefix | SK_ORDER | `SK_ORDER` | STRING | 0 |
| AccessKeyPrefix | *(factory)* | `access` | STRING | 动态 |
| WaitlistKeyPrefix | WL_QUEUE | `wlQueue` | ZSET | 0 |
| WaitlistKeyPrefix | WL_DETAIL | `wlDetail` | HASH | 0 |
| WaitlistKeyPrefix | WL_REPLENISH_LOCK | `wlRepLock` | STRING | 30 s |
| UserKey | getById | `id` | STRING | 0 |
| UserKey | getByName | `name` | STRING | 0 |
