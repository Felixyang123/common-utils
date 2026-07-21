# ADR-0009: cache-aside 缓存写入移到事务提交后（afterCommit）

> **Status**: Accepted
> **Date**: 2026-07-20
> **Context**: admin-server storage 层 cache-aside 模式（`CachedStorageSupport.compute` + `MyBatis*Storage`）

## 背景

admin-server storage 层采用 cache-aside：`CachedStorageSupport.compute(key, ...)` 在 `SyncLock` 内执行"DB 写 + `cache.put`"，外层 `@Transactional` 包裹。`MyBatisConfigStorage`、`MyBatisApiKeyStorage` 等 6 处 `cache.put`/`cache.remove` 都在 `compute` 内、事务内。

## 问题

事务内的副作用（缓存写入、`triggerListeners` 通知）在事务**回滚时不会被撤销**：

- 线程 T1 在事务内 `apiKeyService.add(apiKey)`（JDBC INSERT）→ `cache.put(appId, apiKey)`（缓存立即写入）→ 后续 `createAppEntry` 抛异常 → 事务回滚 → DB 的 INSERT 被回滚，但 `cache.put` **不回滚** → 缓存里留了一个 DB 里不存在的"幽灵 apiKey"。
- 其他请求 `getApiKey(appId)` → 缓存命中 → 返回幽灵 apiKey → 以为 app 已创建 → 后续涉及 DB 的操作行为异常，直到缓存 TTL（api-key 60m）过期自愈。
- `MyBatisConfigStorage.saveConfig` 的 `triggerListeners` 同理：事务回滚后订阅者已收到通知 → pull → 拿到回滚前的错误数据 → 基于错误数据更新池。

这是 cache-aside 写缓存时机与事务边界不一致的经典陷阱。

## 决策

`cache.put` 与 `triggerListeners` 从 `compute` 内移到**事务提交后**（`TransactionSynchronization.afterCommit` / `@TransactionalEventListener(AFTER_COMMIT)`）。回滚时不写缓存、不通知订阅者。

理由：缓存写入和监听器通知都是"事务结果的传播"，语义上应当在事务成功提交后才生效。回滚时它们不应发生。

## 被否的替代

- **回滚时清缓存（afterCompletion + 乐观 put）**：保持 `cache.put` 在事务内，回滚时 `cache.remove`。但"先 put 后回滚清"之间有并发读脏缓存的窗口，且每处 `cache.put` 都要注册清理回调、易遗漏。否决。
- **TTL 自愈**：回滚罕见，靠缓存 TTL（30-60m）过期自愈。但 TTL 内的脏缓存会导致真实问题（幽灵 apiKey、回滚后的旧配置误导订阅者），自愈慢。否决。

## 后果与已知窗口

- 全 storage 层 6 处 `cache.put`/`cache.remove` 统一改为 afterCommit 时机，一致性逻辑收敛。
- **已知不一致窗口（暂不修）**：`compute` 锁在方法 return 时释放，**早于**外层 `@Transactional` 提交。并发场景下，线程 T1 释放锁后事务提交前，T2 拿锁 `getOrLoad` 可能读到旧值并写缓存——靠事务隔离（RR）保证最终一致，但存在短暂不一致。彻底修法是将 `syncLock` 提升到 `@Transactional` 方法外层（锁覆盖整个事务），需重构 `CachedStorageSupport` 与事务边界的分层，侵入性大。当前窗口业务可接受，留 TODO 待未来优化。
