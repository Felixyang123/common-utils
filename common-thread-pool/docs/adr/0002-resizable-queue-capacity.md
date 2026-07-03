---
status: accepted
---

# 队列容量运行时调整采用自研 ResizableCapacityLinkedBlockingQueue

**上下文**：`DynamicThreadPoolWrapper.updateConfig()` 能改 core/max/keepAlive/rejectPolicy，但 `LinkedBlockingQueue` 的 `capacity` 字段是 `private final`，JDK 不提供调整 API。运维需要在服务端改队列容量后，客户端能立即生效，而不必重建池（重建池会导致排队任务丢失或需要迁移，且瞬时中断）。

**决策**：拷贝 JDK `LinkedBlockingQueue` 源码，改造为 `ResizableCapacityLinkedBlockingQueue`，将 `capacity` 改为 `volatile`，提供 `setCapacity(int)` 方法。`LINKED_BLOCKING_QUEUE` 和 `BLOCKING_QUEUE`（默认队列类型）的池在创建时使用此队列，`updateConfig()` 检测到 `queueCapacity` 变更且队列为该类型时，调用 `setCapacity()`。扩容时唤醒因 `count == capacity` 而阻塞的 `put` 线程；缩容允许 `size > capacity`（存量任务照常消费，新 `put` 阻塞直到 size 降回新容量以下）。

**被否的替代**：
- **反射改 `capacity` 字段**：`Field.setAccessible(true)` 在 Java 21 强封装下需要 `--add-opens` JVM 参数，脆弱且不可移植。动态线程池库面对的是任意业务应用，不应要求运维改 JVM 参数。
- **重建池**：`shutdown()` → `new ThreadPoolExecutor(...)` 会导致排队任务丢失或需要迁移（复杂、有乱序风险），且瞬时中断。运维调一个参数不该触发池重建。
- **Hippo4j 同类方案**：Hippo4j 和动态线程池（dynamic-tp）都采用自研可调容量队列，此方案已有成熟先例。

**后果**：`LINKED_BLOCKING_QUEUE` 和 `BLOCKING_QUEUE` 类型的池支持运行时改 `queueCapacity`。`ARRAY_BLOCKING_QUEUE`、`SYNCHRONOUS_QUEUE`、`PRIORITY_BLOCKING_QUEUE` 的容量变更仅 `log.warn` 明示忽略。换队列类型（`queueType` 变更）同样不支持——必须重建池。`defaultIsLinkedBlockingQueue` 测试已更新为验证 `ResizableCapacityLinkedBlockingQueue` 实例。