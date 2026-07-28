# ADR-0001: LocalLockProvider 采用 CHM.compute + 不可变 Entry 真原子化设计

## 状态

accepted (2026-07-19)

## 背景与决策

`LocalLockProvider` 的 04ac596 提交用 `ConcurrentHashMap` 替代了 `Caffeine`,清理了 unlock 时的 lock 实例(`if (!lock.isLocked()) { lockMap.remove(key, lock); }`)来消除锁泄漏。

但这个变更有 **竞态条件**:CHM 与 `ReentrantLock` 是两套独立的同步机制,无论 `unlock → remove` 还是 `remove → unlock` 顺序如何,都不能保证"检查锁状态 + 释放锁 + 删 CHM entry"三个操作原子完成。竞态窗口里其他线程可以先一步 `computeIfAbsent` 拿到同一锁实例,并在误删后创建新锁实例,打破互斥。

我们决定 **`LocalLockProvider` 完全基于 `ConcurrentHashMap.compute` 原子 API 重写**,`LockEntry` 是不可变 POJO(ownerThreadId + lockCount),所有状态迁移在 `compute` lambda 内完成。unlock 时 `lockCount == 0` 则 `return null`,CHM 自动原子删除 entry,**不再需要独立 clean 方法**。

## 关键理由

| 维度 | 设计取舍 |
|------|---------|
| 根本原因 | CHM 的同步边界(entry引用)与 ReentrantLock 的同步边界(锁定状态)不重合,任何外挂顺序都组合不出原子性。唯一解法是让 CHM 的原子操作接管完整的状态迁移流程 |
| 为什么不是先吸脏路径 | `compute` 保证对同一个 key 的两个 `compute` 调用串行;`LockEntry` 不可变保证状态要么全旧要么全新,无中间态;引用只在 compute lambda 内存活,永不逃逸为 orphan |
| `tryLock` 失败语义 | tryLock 失败由上层 `IdempotentExecutionManager.handleConcurrentRequest` 走 `handleLockFailure`(重试),LocalLockProvider 本身不阻塞持锁请求,符合 existing tryLock 接口契约 |
| reentrant 支持 | LockEntry 的 lockCount 在 compute 内 check-then-increment,ownerThreadId 相同时允许 +1;当前代码未明显触发 reentrant 路径,但与外层 IdempotentExecutionManager 的调用拓扑兼容 |
| clean 机制消除 | 内置在 unlock 中(lockCount==0 时直接 CHM remove),避免 standalone clean 方法引入的两阶段同步 |

## 权衡与后果

- **正面**:CHM 的 segment 锁粒度 + LockEntry 不可变语义 = 真正的 per-key 互斥 + 清理原子性;不依赖额外 `ReentrantLock` / 后台线程
- **负面**:compute lambda 在 CHM segment 锁内执行,高并发下同一 segment 的 key 会竞争 segment 锁;但实际业务幂等 key 的碰撞远小于 segment 数量(=默认 16,可调整),几乎不造成瓶颈
- **异常路径**:compute lambda 抛异常时,CHM 自动丢弃修改,不写回 entry;下一轮 compute 也会看到旧状态,锁状态不会损坏
- **revisions**:这是 04ac596 commit 的补充修复,04ac596 只替换了 Caffeine → CHM 但没有解决 unlock 竞态;此 ADR 是完整解决方案

## 相关文件

- `idempotent/src/main/java/com/lezai/idempotent/lock/LocalLockProvider.java`
- `idempotent/src/test/java/com/lezai/idempotent/lock/LocalLockProviderTest.java`
