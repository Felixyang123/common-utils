# 阶段 1.2 · 生命周期与退避 · 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: subagent-driven-development or executing-plans.

**Goal:** SmartLifecycle 统一编排启停、资源释放（scheduler + OkHttp）、优雅停机重写（同步有界）、长轮询指数退避。

**Architecture:** Cluster A（生命周期）→ Cluster B（退避），顺序执行。修改集中在 4 个文件 + 新增测试。

**Dependency:** Plan 1.1 已完成（`dynamic-tp` 分支，`079077d`）。

---

### Task 1: ThreadPoolManager 优雅停机重写

**Files:** Modify `ThreadPoolManager.java`

当前问题：
- 内部类 `CompletableFuture`（同名 JDK 类混淆）含 static daemon `ScheduledThreadPoolExecutor(2)` 永不关闭
- `shutdown()` 异步关停后立即 `clear()`，在途任务可能未完成
- `shutdownPoolAsync` 硬编码 30s 超时

修改：
1. **删除** private static inner class `CompletableFuture`（约 line 242-257）
2. **重写 `shutdown()`**：同步遍历 `poolRegistry`，逐池 `shutdown()` → `awaitTermination(30, SECONDS)` → 超时 `shutdownNow()`。等待结束后 `poolRegistry.clear()`。日志明确。
3. **简化 `removePool`**：不再调 `shutdownPoolAsync`，改用 inline 关闭（`pool.shutdown()` + try await 短超时 + finally shutdownNow）
4. **简化 `removeAllPools`**：同步关闭每个池后 clear
5. **简化 `recreatePool`**（若存在）：同上

测试（扩展现有 `ThreadPoolManagerTest`）：
- `shutdown` 真正阻塞等待（提交任务 → shutdown → 验证 `isTerminated()` true）
- `shutdown` 无池时不抛异常
- `removePool` 关停了池

---

### Task 2: RemoteConfigSourceDetector.stop() 补全资源释放

**Files:** Modify `RemoteConfigSourceDetector.java`

当前 `stop()` 只设 `running=false` + `interrupt()` subscriptionThread。

修改 `stop()`：
```java
public void stop() {
    if (!running) return;
    running = false;
    log.info("Stopping remote config source for appId: {}", appId);
    subscriptionThread.interrupt();
    // 关闭调度器
    pullConfigsScheduler.shutdown();
    try {
        if (!pullConfigsScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
            pullConfigsScheduler.shutdownNow();
        }
    } catch (InterruptedException e) {
        pullConfigsScheduler.shutdownNow();
        Thread.currentThread().interrupt();
    }
    // 关闭 OkHttp
    httpClient.dispatcher().executorService().shutdown();
    httpClient.connectionPool().evictAll();
    log.info("Remote config source stopped for appId: {}", appId);
}
```

需要 `import java.util.concurrent.TimeUnit`（应该已有）。

---

### Task 3: ThreadPoolStatsReporter.stop() 补全资源释放

**Files:** Modify `ThreadPoolStatsReporter.java`

当前 `stop()` 只 `reportExecutor.shutdown()`，不释放 `httpClient`。

修改 `stop()`：在 `reportExecutor.shutdown()` 后加 `awaitTermination` + 关闭 `httpClient`（同 Task 2 模式）。

---

### Task 4: RemoteConfigSourceDetector 长轮询指数退避

**Files:** Modify `RemoteConfigSourceDetector.java`

当前 `subscribeWithLongPolling()` 的异常分支 `Thread.sleep(10)` — 无退避。

修改：新增实例字段 `private long backoffMs = 0;`，在异常分支：
```java
if (backoffMs == 0) backoffMs = 1000L;
else backoffMs = Math.min(backoffMs * 2, 30000L);
Thread.sleep(backoffMs);
```
成功收到数据后重置 `backoffMs = 0;`。
同时区分"正常超时（无变更）"和"异常"：HTTP 200 但 `analyzeResponse` 返回 null → 正常，不触发退避，仅 `log.debug`。
退避初始/上限值可配置（新增 `ThreadPoolProperties.RemoteConfig` 字段：`backoffInitialMs` 默认 1000，`backoffMaxMs` 默认 30000）。

测试（`MockWebServer`）：
- server 返回 500 → 退避递增 → 达到上限封顶
- server 恢复 → 退避重置

---

### Task 5: ThreadPoolAutoConfiguration SmartLifecycle 编排

**Files:** Modify `ThreadPoolAutoConfiguration.java`

当前：两个 `ApplicationListener<ContextClosedEvent>` 分散关停；`reporter.start()` / `initializer.initialize()`（含 HTTP 首拉）在 `@Bean` 构造期 eager 调用。

修改：
1. **删除** `reporterDestroyListener` 和 `detectorDestroyListener` 两个 `@Bean` 方法
2. **将 `reporter.start()` 移到 `SmartLifecycle.start()`**：为 reporter bean 实现 `SmartLifecycle`，`start()` 调 `reporter.start()`
3. **将 `initializer.initialize()` 从 @Bean 构造期移到 `SmartLifecycle.start()`**：为 initializer 实现 `SmartLifecycle`
4. **停机关闭顺序**（通过 phase 控制）：
   - detector & reporter：`phase = 0`（先停）
   - ThreadPoolManager shutdown：`phase = Integer.MAX_VALUE`（最后停）
5. 注入 `ThreadPoolManager` bean 到关闭组件中

或更简洁：创建**单个** `ThreadPoolLifecycle` bean 实现 `SmartLifecycle`，内部持有 detector/reporter/manager 引用，在 `start()` 调 initializer + reporter.start + detector.start，在 `stop()` 按序调 detector.stop + reporter.stop + manager.shutdown。**推荐此方案**——集中编排，不侵入每个组件。

---

### Task 6: 全量回归

```sh
mvn -pl client-sdk -am clean test -Dfile.encoding=UTF-8
mvn -pl admin-server -am clean compile -DskipTests
```

---

## 文件清单

- MODIFY: `ThreadPoolManager.java`, `RemoteConfigSourceDetector.java`, `ThreadPoolStatsReporter.java`, `ThreadPoolAutoConfiguration.java`, `ThreadPoolProperties.java`（backoff 配置字段）
- CREATE: `RemoteConfigSourceDetectorTest.java`、扩展 `ThreadPoolManagerTest`
