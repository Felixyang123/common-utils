# ADR-0004: 锁即租约的 PROCESSING 崩溃接管

## 状态

accepted (2026-07-26)

## 背景与决策

幂等执行者（持有锁、正在执行业务）可能因 JVM 崩溃、kill -9、网络分区等原因突然死亡。此时幂等记录停留在 PROCESSING 状态，锁由看门狗停止续期后自然过期（≈ leaseTime），但 PROCESSING 记录要等 `expireTime`（默认 1h）才过期。在此窗口内，同 key 的后续请求全部 fail-fast——该 key 完全不可用。

我们决定 **以「锁的存在性」作为执行者存活性的代理信号**（锁即租约）。处理 PROCESSING 记录时，先检查锁是否仍被持有：锁已死 → 执行者已崩 → 走接管路径（获取锁 → 转 FAILED → 重执行）；锁仍活 → 执行者健在 → 维持原 fail-fast / wait-retry。

## 关键理由

| 维度 | 分析 |
|------|------|
| 为什么用锁而非心跳/租约字段 | common-lock 的 WatchDogExecutor 已实现锁续期（每 leaseTime/3），锁即租约复用现有机制，无需引入额外心跳表或记录级看门狗。复杂度为零 |
| 崩溃窗口从多久缩到多久 | 从 `expireTime`（默认 1h）缩到 ≈ `lockLeaseTime`（默认 60s）——看门狗停续后锁在 leaseTime 内过期 |
| 误判风险（锁过期但执行者还活着） | 如果续约线程因 GC 暂停 > leaseTime，锁可能在业务执行期间过期，另一实例接管 → 两个实例并发执行。这是 Redis 锁的固有限制，leaseTime=60s + 续约间隔 20s 给了 40s 的 GC margin，实际风险极低 |
| 与 FAILED 冷却的配合 | 接管路径走 handleFailRecord → 冷却判断 → 若 failCount 未达上限则重执行。多次接管失败后自动冷却，避免无限重试 |

## 决策影响

- **IdempotentLockProvider 接口新增** `boolean isLocked(String key)`——判断锁是否被任意线程/实例持有
- **LocalLockProvider**：`lockMap.containsKey(key)` 实现
- **RedisLockProvider**：委托 `RedisDistributeLock.isLocked(lockKey)`
- **common-lock RedisDistributeLock 新增** `isLocked(key)` 方法：`redisTemplate.hasKey(key)`
- **IdempotentExecutionManager.handleProcessingRecord**：开头加 `!lockProvider.isLocked(key)` → `handleFailRecord` 接管路径
- **lockLeaseTime 默认 60s**：与看门狗续约（20s）配合，平衡崩溃检测速度与 GC 容忍度

## 权衡与后果

- **正面**：崩溃窗口从 1h 缩到 60s，同 key 可用性大幅改善；复用现有看门狗，零新增基础设施
- **负面**：Redis 锁固有的误判风险（GC/网络分区导致锁过期但业务仍执行）；LocalLockProvider 的 isLocked 仅检查 entry 存在（Thread.stop 等极端场景 entry 可能残留），Local 降级路径的风险已被文档化
- **TOCTOU 窗口**：检查 isLocked → 获取锁之间有微小窗口，但 handleFailRecord 的双重检查 + 锁互斥保证了安全性——最多多执行一次（at-least-once）

## 相关文件

- `idempotent/src/main/java/com/lezai/idempotent/lock/IdempotentLockProvider.java`（接口）
- `idempotent/src/main/java/com/lezai/idempotent/lock/LocalLockProvider.java`
- `idempotent/src/main/java/com/lezai/idempotent/lock/RedisLockProvider.java`
- `common-lock/src/main/java/com/lezai/lock/RedisDistributeLock.java`
- `idempotent/src/main/java/com/lezai/idempotent/core/IdempotentExecutionManager.java`（handleProcessingRecord）
