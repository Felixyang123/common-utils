# 2026-07-20 · 企业级代码评审决议 · 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: subagent-driven-development.

**Goal:** 落地 2026-07-20 企业级代码评审（`ocr` + 人工精读）的 18 条决议，覆盖并发/缓存一致性缺陷、安全加固、可观测性口径偏差、分页性能。每条决议已在 grill 讨论中达成共识，并沉淀为 3 份 ADR（0007/0008/0009）+ CONTEXT.md 1 条领域边界。

**Architecture:** 4 个 batch（A 正确性 / B 安全 / C 一致性健壮性 / D 低优改进），batch 间串行（B 依赖 A 的指标口径、C 依赖 B 的限流基础设施），batch 内可并行。

**Dependency:** 现有 `dynamic-tp` 分支基线；CONTEXT.md 领域边界已更新；ADR-0007/0008/0009 已起草。

**关联文档:**
- [CONTEXT.md §池的访问规则](../../CONTEXT.md) — "服务端下发不产生池"边界
- [ADR-0007](../../adr/0007-apikey-hmac-sha256.md) — API Key HMAC-SHA256
- [ADR-0008](../../adr/0008-applyconfigs-no-create-undeclared-pool.md) — applyConfigs 不创建未声明池
- [ADR-0009](../../adr/0009-cache-aside-after-commit.md) — cache-aside afterCommit

---

## File Structure

```
common-thread-pool/
├── CONTEXT.md                                        ← 已更新（本轮评审已完成）
├── docs/adr/
│   ├── 0007-apikey-hmac-sha256.md                    ← 已创建
│   ├── 0008-applyconfigs-no-create-undeclared-pool.md← 已创建
│   └── 0009-cache-aside-after-commit.md              ← 已创建
│
├── admin-server/src/main/java/com/lezai/threadpool/
│   ├── service/
│   │   ├── OpenThreadPoolConfigService.java          ← MODIFY (Batch A: subscribe TOCTOU + 泄漏 + 日志)
│   │   ├── ThreadPoolConfigPersistenceService.java   ← MODIFY (Batch A: addConfigApp 不涨版本/不trigger; Batch C: restoreConfigsByAppId 审计)
│   │   ├── StatsAlertService.java                    ← 不动（已确认无 race）
│   │   ├── ConfigAdminService.java                   ← MODIFY (Batch C: createApp afterCommit 时机由 storage 层接管)
│   │   └── OperateLogService.java                    ← MODIFY (Batch D: getRecentLogs 改游标 + queryLogs 时间范围)
│   ├── storage/
│   │   ├── MyBatisConfigStorage.java                 ← MODIFY (Batch A: deleteConfigs Long.MAX_VALUE + 删冗余 unregister; Batch C: cache/trigger 移 afterCommit)
│   │   ├── MyBatisApiKeyStorage.java                 ← MODIFY (Batch C: cache 移 afterCommit)
│   │   ├── cache/CachedStorageSupport.java           ← MODIFY (Batch C: 提供 putAfterCommit 封装 + 锁/事务窗口 TODO)
│   │   ├── cache/Cache.java                          ← 不动（NULL_MARKER 逻辑正确，问题在 getOrLoad）
│   │   └── listener/ConfigChangeListenerManager.java ← MODIFY (Batch A: triggerListeners 补 Javadoc)
│   ├── util/
│   │   ├── LocalStripedLock.java                     ← MODIFY (Batch A: 删 tryLock 前置检查 + unlock 加 isHeldByCurrentThread)
│   │   └── SyncLock.java                             ← MODIFY (Batch C: tryLock 补 Javadoc 说明 leaseTime 不续期)
│   ├── utils/
│   │   ├── ApiKeyUtils.java                          ← MODIFY (Batch B: BCrypt → HMAC-SHA256)
│   │   └── PasswordUtils.java                        ← 不动（管理员密码保持 BCrypt）
│   ├── interceptor/
│   │   ├── ApiKeyAuthInterceptor.java                ← MODIFY (Batch B: 失败请求独立限流)
│   │   └── RateLimitInterceptor.java                 ← MODIFY (Batch B: appId 为空按 IP 限流)
│   ├── service/AdminAuthService.java                 ← MODIFY (Batch B: 统一错误消息)
│   ├── service/AdminUserManagementService.java       ← 不动
│   ├── config/AdminAuthProperty.java                 ← 不动（默认值保留，由启动校验拦截）
│   ├── config/AdminServerAutoConfiguration.java      ← MODIFY (Batch B: db profile 启动校验默认凭据)
│   ├── config/AsyncExecutorConfig.java               ← MODIFY (Batch D: listenerNotifyExecutor 补 TODO)
│   ├── dao/mapper/ThreadPoolConfigMapper.java        ← MODIFY (Batch C: 新增 selectDeletedByAppId)
│   ├── dao/mapper/OperateLogMapper.java              ← MODIFY (Batch D: 游标查询 SQL)
│   └── open/OpenThreadPoolConfigController.java      ← MODIFY (Batch A: RejectedExecutionException → 503)
│
├── admin-server/src/main/resources/
│   ├── schema.sql                                    ← MODIFY (Batch D: operate_log 加 idx_create_time + idx_biz)
│   └── application.yml                               ← 不动（null-value-ttl 保留）
│
├── admin-server/src/main/resources/static/
│   └── index.html                                    ← MODIFY (Batch D: 活动流"加载更多"游标交互)
│   └── operate-logs.html                             ← MODIFY (Batch D: 时间范围筛选)
│
├── client-sdk/src/main/java/com/lezai/threadpool/
│   ├── core/DynamicThreadPoolWrapper.java            ← MODIFY (Batch A: wrap 统一 error+completed 计数; submit 改自建 CompletableFuture; getStats Javadoc)
│   ├── aspect/AsyncExecutionSupport.java             ← MODIFY (Batch A: 去掉 whenComplete 的 incrementErrorCount)
│   ├── client/ConfigPollingService.java              ← MODIFY (Batch A: applyConfigs 不创建未声明池)
│   ├── core/ResizableCapacityLinkedBlockingQueue.java← MODIFY (Batch D: take() 补注释)
│   └── manager/ThreadPoolManager.java                ← MODIFY (Batch D: 删 TODO 死锁实验注释)
│
└── client-sdk/src/test/java/com/lezai/threadpool/
    └── manager/ThreadPoolManagerReentrancyTest.java   ← CREATE (Batch D: computeIfAbsent 内回调死锁回归测试)
```

---

## 执行策略

4 个 batch 串行，batch 内 task 可并行。每个 task 独立可编译、可测试。

---

### Batch A：正确性（subscribe 重构 + 指标口径 + 语义对齐）

> 互相关联的一组：subscribe 的 TOCTOU/泄漏/503、客户端指标计数、applyConfigs 语义对齐、deleteConfigs 退管通知、LocalStripedLock。这些改动触及同一批文件且语义耦合，一起改一起测。

---

#### Task A1: subscribe 重构（TOCTOU + 泄漏 + 503 + 日志分级）

**Files:** `OpenThreadPoolConfigService.java`, `OpenThreadPoolConfigController.java`, `ConfigChangeListenerManager.java`

- [ ] **Step 1: subscribe 改「先注册后比较」序**

  `OpenThreadPoolConfigService.subscribe`（第 73-132 行）重排：
  1. `getAppConfig(appId)` 仅校验存在性
  2. 构造 listener → `listenerManager.register(appId, listener)` **先注册**
  3. 再读 `currentVersion`；若 `> version` → `future.complete()` + 立即 `unregister`（幂等，listener 可能已被 triggerListeners 摘除，`computeIfPresent` 空操作安全）
  4. `try { schedule 补偿定时器 } catch (RejectedExecutionException e) { future.completeExceptionally(e); }`（不抛出，让 whenComplete 接管）
  5. `future.whenComplete` → `unregister` + `cancelCompensation`（兜底幂等）

- [ ] **Step 2: 补偿定时器移到超时临近（决议第 1 题 B 方案）**

  `schedule(..., Math.min(1000, timeoutMs), ...)` 改为 `schedule(..., Math.max(timeoutMs - 500, 1000), ...)`——补偿定时器回归「订阅快超时时兜底确认版本」语义。注意下界保留 1000ms（timeoutMs 极小时）。

- [ ] **Step 3: 日志分级（决议第 17 题）**

  第 109/115/130 行 `log.info` → `log.debug`（常规生命周期）；第 87/97 行保留 `info`（有效变更通知）。

- [ ] **Step 4: RejectedExecutionException → 503**

  `OpenThreadPoolConfigController.subscribe`（第 50-69 行）：`future.whenComplete` 的 `ex != null` 分支，识别 `RejectedExecutionException` → `ResponseEntity.status(503).header("Retry-After", "5").build()`；其他异常保持 `setErrorResult`。

- [ ] **Step 5: triggerListeners 补 Javadoc（决议第 3 题连带）**

  `ConfigChangeListenerManager.triggerListeners` 补 Javadoc：「调用方必须保证 version 单调递增（配置变更后 ensureConfigApp 涨版本）或语义为整 app 退管（用 Long.MAX_VALUE）。整表 remove 后回调 listener，listener 内部判 `newVersion > version` 决定是否 complete。」

- [ ] **Step 6: 测试**

  更新 `OpenThreadPoolConfigServiceTest`：新增「先注册后比较」序的回归测试（模拟 register 与 version 检查之间发生 trigger，验证不丢通知）；新增 `RejectedExecutionException` 返回 503 的测试（用饱和 executor）。

  ```bash
  mvn -pl admin-server -am clean test -Dtest=OpenThreadPoolConfigServiceTest,OpenThreadPoolConfigControllerTest
  ```

---

#### Task A2: 客户端指标口径对齐（error 下沉 wrap + submit 共享计数 + 去重）

**Files:** `DynamicThreadPoolWrapper.java`, `AsyncExecutionSupport.java`

- [ ] **Step 1: wrap 统一 error + completed 计数**

  `DynamicThreadPoolWrapper.wrap`（第 71-79 行）改为：
  ```java
  private Runnable wrap(Runnable command) {
      return () -> {
          try {
              command.run();
          } catch (Throwable e) {
              errorTaskCount.incrementAndGet();
              throw e;
          } finally {
              completedTaskCount.incrementAndGet();
          }
      };
  }
  ```
  语义：completed = 成功 + 错误（CONTEXT.md 口径），error ⊂ completed。

- [ ] **Step 2: submit 改自建 CompletableFuture 共享 wrap（决议第 7 题决策 3 的 (c)）**

  `submit(Callable)`（第 83-90 行）改为不依赖 `supplyAsync(..., this)`，自建 CompletableFuture + `this.execute(wrap(...))`，让 submit 也走 wrap 的统一计数。去掉 submit 的 `whenComplete(incrementErrorCount)`（error 已由 wrap 计）。

- [ ] **Step 3: 去掉 AsyncExecutionSupport 的重复 error 计数**

  `AsyncExecutionSupport.java` 第 41 行 `pool.incrementErrorCount()` 删除（error 由 wrap 统一计，AOP 路径走 execute → wrap）。保留 `log.error` 日志。

- [ ] **Step 4: getStats Javadoc（决议第 12 题）**

  `getStats()` 补 Javadoc：「指标为非一致快照，updateConfig 并发时可能出现瞬时矛盾值（如 core>max），下一采集周期自愈。」

- [ ] **Step 5: CONTEXT.md 删除 started 定义（决议第 7 题决策 2）**

  `CONTEXT.md` §任务计数术语 删除 "started（已开始）" 条目（当前不实现，保持四计数器）。

- [ ] **Step 6: 测试**

  更新 `DynamicThreadPoolWrapper` 相关测试：execute 正常/异常路径验证 submitted/completed/error 计数；submit 路径验证 completed 计入。

  ```bash
  mvn -pl client-sdk -am clean test -Dtest=DynamicThreadPoolWrapperTest
  ```

---

#### Task A3: 语义对齐（applyConfigs 不创建 + addConfigApp 不广播 + deleteConfigs 退管通知）

**Files:** `ConfigPollingService.java`, `ThreadPoolConfigPersistenceService.java`, `MyBatisConfigStorage.java`

> 依赖 ADR-0008。这一组是"服务端非池创建指令源"原则的落地。

- [ ] **Step 1: applyConfigs 不创建未声明池**

  `ConfigPollingService.applyConfigs`（第 167-179 行）：对每个 config，先 `threadPoolManager.getPool(name)` 判存在——存在则 `upsertPool`（更新），不存在则 `log.warn("skip undeclared pool {} for appId {}, not in local declaration", ...)` 跳过，不创建。

  注意：`upsertPool` 本身"有则更新无则创建"，所以必须用 `getPool` 预判，不能直接 `upsertPool`。或改用 `updatePool`（仅更新，不存在抛 PoolNotFoundException → catch 后跳过）。**选 `getPool` 预判 + `updatePool`**，避免 upsertPool 的创建语义。

- [ ] **Step 2: addConfigApp 不涨版本、不 trigger**

  `ThreadPoolConfigPersistenceService.addConfigApp`（第 192-220 行）：
  - `ensureConfigApp(appId)`（第 193 行）改为不涨 version 的版本——提取 `ensureConfigAppNoVersionInc` 或在 addConfigApp 内自行处理 app entry 存在性（不存在才创建 version=0，存在不动）。
  - `MyBatisConfigStorage.addConfigs`（第 103-108 行）保持不调 `triggerListeners`（现状已不调，确认即可）。

- [ ] **Step 3: deleteConfigs 退管通知修复**

  `MyBatisConfigStorage.deleteConfigs`（第 61-71 行）：`triggerListeners(appId, version)` 改为 `triggerListeners(appId, Long.MAX_VALUE)`；删除第 69 行冗余的 `listenerManager.unregister(appId)`（triggerListeners 已整表 remove）。

- [ ] **Step 4: 测试**

  - applyConfigs：新增"服务端下发本地未声明的池 → 不创建"测试。
  - addConfigApp：新增"重推已存在池 → version 不涨、不 trigger"测试。
  - deleteConfigs：新增"整 app 删除 → listener 收到 Long.MAX_VALUE → complete → 客户端感知"测试。

  ```bash
  mvn -pl client-sdk,admin-server -am clean test -Dtest=ConfigPollingServiceTest,MyBatisConfigStorageTest,ThreadPoolConfigPersistenceServiceTest
  ```

---

#### Task A4: LocalStripedLock 重入/解锁一致

**Files:** `LocalStripedLock.java`

- [ ] **Step 1: 删 tryLock 前置检查**

  `LocalStripedLock.tryLock`（第 30-36 行）删除 `if (lock.isHeldByCurrentThread()) return false;`，直接 `return lock.tryLock(waitTime, timeUnit)`，与 `ReentrantLock` 原生重入语义对齐。

- [ ] **Step 2: unlock 加 isHeldByCurrentThread 保护**

  `unlock`（第 39-41 行）改为：
  ```java
  public void unlock(String lockName, String key) {
      ReentrantLock lock = locks[stripeIndex(lockName, key)];
      if (lock.isHeldByCurrentThread()) {
          lock.unlock();
      }
  }
  ```
  与 `RedissonSyncLock.unlock` 行为对齐。

- [ ] **Step 3: 测试**

  新增 `LocalStripedLockTest`：同线程重入 tryLock 返回 true；未持锁 unlock 不抛异常。

  ```bash
  mvn -pl admin-server -am clean test -Dtest=LocalStripedLockTest
  ```

---

### Batch B：安全（API Key HMAC + 失败限流 + 登录加固 + 默认凭据校验）

> 依赖 Batch A 完成（失败限流复用 A 的限流基础设施思路）。B 内 task 可并行。

---

#### Task B1: API Key 改 HMAC-SHA256（ADR-0007）

**Files:** `ApiKeyUtils.java`, `application.yml`（新增 HMAC 密钥配置）, `AdminAuthProperty.java` 或独立配置类

- [ ] **Step 1: ApiKeyUtils 改 HMAC-SHA256**

  `hashApiKey` / `validateApiKey` 改用 `javax.crypto.Mac` + `HmacSHA256`，密钥从配置注入（不再用静态 `BCryptPasswordEncoder`）。`ApiKeyUtils` 改为实例方法或接收密钥参数（因需密钥）。

- [ ] **Step 2: 配置 HMAC 密钥**

  `application.yml` 新增 `threadpool.admin.apikey.hmac-secret`（默认值 + 启动校验，见 B4）。db profile 下不得为默认值。

- [ ] **Step 3: 调用方适配**

  `ConfigAdminService.createApp`（第 131 行 `ApiKeyUtils.hashApiKey`）和 `MyBatisApiKeyStorage.regenerateApiKey`（第 127 行）改为注入新的 ApiKeyUtils 实例（带密钥）。

- [ ] **Step 4: 强制重建存量**

  无生产数据，文档说明"升级后需重新 createApp"。无需迁移代码。

- [ ] **Step 5: 测试**

  更新 `ApiKeyUtils` 测试：HMAC 哈希/校验；不同密钥产生不同 hash。

  ```bash
  mvn -pl admin-server -am clean test -Dtest=ApiKeyUtilsTest
  ```

---

#### Task B2: API Key 失败请求独立限流（决议第 5 题方向 Y）

**Files:** `ApiKeyAuthInterceptor.java`, `RateLimitInterceptor.java`, 新增 `ApiKeyFailureRateLimiter` 或复用 Redisson

- [ ] **Step 1: 失败计数器**

  新增 per-appId 失败计数（Redisson `RAtomicLong` + 60s TTL）。`validateApiKey` 失败时 incr，超 10 次 → 后续请求直接 429（不再查缓存/跑 HMAC），TTL 内自然恢复。

- [ ] **Step 2: appId 为空按 IP 限流**

  `RateLimitInterceptor`（第 33-43 行）：`appId` 为空时不再 `return true`，改为按 `request.getRemoteAddr()` 维度限流（独立 key 前缀），防止无 appId 无效请求洪流。

- [ ] **Step 3: 失败请求不挤占合法配额**

  确保 RateLimitInterceptor 的 100/60s 配额只计成功认证请求；失败请求走 B2 Step1 的独立计数器。可能需调整拦截器顺序或区分计数。

- [ ] **Step 4: 测试**

  新增限流测试：连续 10 次错 key → 第 11 次 429；成功认证不触发失败计数。

  ```bash
  mvn -pl admin-server -am clean test -Dtest=ApiKeyAuthInterceptorTest,RateLimitInterceptorTest
  ```

---

#### Task B3: 登录加固（枚举 + 暴力破解防护）

**Files:** `AdminAuthService.java`, 新增 `AdminLoginRateLimiter`

- [ ] **Step 1: 统一错误消息**

  `AdminAuthService.login`（第 47-56 行）：用户不存在、密码错误统一抛 `AuthenticationException("用户名或密码错误")`，不区分。

- [ ] **Step 2: 失败锁定**

  per-username Redisson 失败计数（`RAtomicLong` + 15min TTL）。连续失败 5 次 → 锁定该 username 15min，期间直接拒绝（401 + 提示锁定）。成功登录清零计数。

- [ ] **Step 3: 测试**

  新增：5 次失败 → 第 6 次锁定；成功登录清零；锁定 15min 后恢复（用 TTL mock）。

  ```bash
  mvn -pl admin-server -am clean test -Dtest=AdminAuthServiceTest
  ```

---

#### Task B4: 默认凭据启动校验（决议第 6c + 17 题）

**Files:** `AdminServerAutoConfiguration.java`, `AdminAuthProperty.java`

- [ ] **Step 1: db profile 启动校验**

  `AdminServerAutoConfiguration` 启动时（`@PostConstruct` 或 `ApplicationRunner`）检测 active profile，若为 `db`：
  - `authProperty.password == "changeme"` → fail-fast
  - `authProperty.secret == 默认值` → fail-fast
  - `datasource.password == "lifan1994"` → fail-fast
  - `apikey.hmac-secret == 默认值`（B1 新增）→ fail-fast

  local profile 放行（开发便利）。

- [ ] **Step 2: 测试**

  新增：db profile + 默认凭据 → 启动失败；db profile + 改过凭据 → 启动成功；local profile + 默认凭据 → 启动成功。

  ```bash
  mvn -pl admin-server -am clean test -Dtest=DefaultCredentialStartupTest
  ```

---

### Batch C：一致性/健壮性（负缓存 + 审计 + cache-aside afterCommit + 锁 Javadoc + executor TODO）

> 依赖 Batch B 的限流基础设施（C 的 cache-aside 重构独立，但测试环境需 B 的 Redisson 配置就绪）。C 内 task 可并行。

---

#### Task C1: 负缓存修复（决议第 8 题方向 A）

**Files:** `CachedStorageSupport.java`

- [ ] **Step 1: getOrLoad 区分未缓存 vs 缓存 null**

  `getOrLoad`（第 37-53 行）改用 `cache.containsKey(key)` 或 `rawGet`：
  - `containsKey` 返回 true 且值为 NULL_MARKER → 返回 null（负缓存命中，不查 DB）
  - `containsKey` 返回 true 且值为真实对象 → 返回它
  - `containsKey` 返回 false → 走 compute 加载

  注意 `CaffeineCache.containsKey`（第 42 行）用 `getIfPresent != null`，NULL_MARKER 是非 null 对象 → 返回 true ✅；`RedissonCache.containsKey`（第 56 行）用 `delegate.containsKey` → 存了 NULL_MARKER 返回 true ✅。两个实现都支持。

- [ ] **Step 2: 测试**

  新增：查询不存在的 appId → 缓存 NULL_MARKER → 再次查询不查 DB（验证负缓存命中）。

  ```bash
  mvn -pl admin-server -am clean test -Dtest=CachedStorageSupportTest
  ```

---

#### Task C2: restoreConfigsByAppId 审计补全（决议第 9 题）

**Files:** `ThreadPoolConfigMapper.java`, `ThreadPoolConfigPersistenceService.java`

- [ ] **Step 1: 新增 selectDeletedByAppId**

  `ThreadPoolConfigMapper` 加：
  ```java
  @Select("SELECT * FROM thread_pool_config WHERE deleted = 1 AND app_id = #{appId}")
  List<ThreadPoolConfigEntity> selectDeletedByAppId(@Param("appId") String appId);
  ```

- [ ] **Step 2: restoreConfigsByAppId 恢复前拉列表入审计**

  `ThreadPoolConfigPersistenceService.restoreConfigsByAppId`（第 239-245 行）：
  ```java
  List<ThreadPoolConfigEntity> deletedConfigs = configRep.getBaseMapper().selectDeletedByAppId(appId);
  configRep.getBaseMapper().restoreByAppId(appId);
  configAppRep.getBaseMapper().restore(appId);
  publishAuditEvent(deletedConfigs, OperateType.RESTORE);
  ```

- [ ] **Step 3: 测试**

  新增：restoreConfigsByAppId 后审计日志含每条恢复配置的详情。

  ```bash
  mvn -pl admin-server -am clean test -Dtest=ThreadPoolConfigPersistenceServiceTest
  ```

---

#### Task C3: cache-aside afterCommit（ADR-0009）

**Files:** `CachedStorageSupport.java`, `MyBatisConfigStorage.java`, `MyBatisApiKeyStorage.java`

> 这是 Batch C 最重的改动，触及全 storage 层 6 处 cache.put。

- [ ] **Step 1: CachedStorageSupport 提供 putAfterCommit 封装**

  新增辅助方法，内部注册 `TransactionSynchronization`：提交 → `cache.put`，回滚 → 不操作。
  ```java
  protected void putAfterCommit(String key, T value) {
      if (TransactionSynchronizationManager.isSynchronizationActive()) {
          TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
              @Override public void afterCommit() { cache.put(key, value); }
          });
      } else {
          cache.put(key, value);  // 无事务（local 边界场景）直接 put
      }
  }
  ```

- [ ] **Step 2: 6 处 cache.put 改 putAfterCommit**

  - `MyBatisConfigStorage.saveConfig`（第 45 行）
  - `MyBatisConfigStorage.deleteConfig`（第 78 行）
  - `MyBatisApiKeyStorage` 第 52/106/135 行
  - `cache.remove`（`MyBatisConfigStorage.deleteConfigs` 第 67 行）同理改为 `removeAfterCommit`

- [ ] **Step 3: triggerListeners 一并移到 afterCommit**

  `MyBatisConfigStorage.saveConfig/deleteConfig/deleteConfigs` 的 `triggerListeners` 调用移到 afterCommit 回调内（与 cache.put 同时机）。回滚不通知订阅者。

- [ ] **Step 4: compute 锁/事务窗口 TODO（决议第 14 题连带 2）**

  `CachedStorageSupport.compute`（第 55 行）补 TODO 注释（见 ADR-0009 后果节）。

- [ ] **Step 5: 测试**

  新增：事务回滚后缓存不含脏数据（mock `createAppEntry` 抛异常，验证 `getApiKey` 不命中幽灵）；事务提交后缓存写入；triggerListeners 在提交后触发。

  ```bash
  mvn -pl admin-server -am clean test -Dtest=MyBatisConfigStorageTest,MyBatisApiKeyStorageTest
  ```

---

#### Task C4: SyncLock.tryLock 补 Javadoc（决议第 10b 题）

**Files:** `SyncLock.java`

- [ ] **Step 1: tryLock Javadoc**

  `SyncLock.tryLock` 补 Javadoc：「`leaseTime` 后锁强制释放，**不续期**（与 `lock()` 的 watchdog 续期行为不同）。适用于"抢锁跑定时任务、超时让位"语义（如 `StatsAggregationService`）。持锁操作须在 leaseTime 内完成，否则锁易主可能导致重复执行。」

- [ ] **Step 2: 编译验证**

  ```bash
  mvn -pl admin-server -am clean compile -DskipTests
  ```

---

#### Task C5: listenerNotifyExecutor TODO（决议第 11 题）

**Files:** `AsyncExecutorConfig.java`

- [ ] **Step 1: 补 TODO**

  `listenerNotifyExecutor`（第 54 行）上方补 TODO（内容见评审决议第 11 题）。

---

### Batch D：低优改进（索引+游标 + 死锁测试 + 日志 + 注释）

> 独立于 A/B/C，可随时执行。D 内 task 可并行。

---

#### Task D1: operate_log 索引 + 游标查询 + queryLogs 时间范围（决议第 13 题）

**Files:** `schema.sql`, `OperateLogMapper.java`, `OperateLogService.java`, `DashboardController.java`, `index.html`, `operate-logs.html`

- [ ] **Step 1: schema 加索引**

  `schema.sql` 的 `operate_log` 表加：
  ```sql
  INDEX idx_create_time (create_time),
  INDEX idx_biz (biz_type, biz_id)
  ```
  注意：local profile 用 H2 `mode=always` 每次重建，加索引即生效；db profile 需手动迁移。

- [ ] **Step 2: 新增游标查询 mapper**

  `OperateLogMapper` 加按 `(create_time DESC, id DESC)` 游标的方法：
  ```java
  @Select("<script>SELECT * FROM operate_log WHERE deleted = 0 " +
          "<if test='lastCreateTime != null'> AND (create_time &lt; #{lastCreateTime} OR (create_time = #{lastCreateTime} AND id &lt; #{lastId})) </if>" +
          " ORDER BY create_time DESC, id DESC LIMIT #{limit}</script>")
  List<OperateLogEntity> selectByCursor(@Param("lastCreateTime") LocalDateTime lastCreateTime,
                                        @Param("lastId") Long lastId,
                                        @Param("limit") int limit);
  ```

- [ ] **Step 3: getRecentLogs 改游标**

  `OperateLogService.getRecentLogs(limit, offset)` 签名改为 `getRecentLogs(int limit, LocalDateTime lastCreateTime, Long lastId)`，调游标查询。消除字符串拼接 LIMIT。

- [ ] **Step 4: queryLogs 强制时间范围**

  `OperateLogService.queryLogs` 加 `startTime`/`endTime` 参数，wrapper 加 `between(create_time)`。默认最近 7 天。

- [ ] **Step 5: 前端加载更多**

  `index.html` 活动流加"加载更多"按钮，带游标参数（`lastCreateTime`/`lastId`）调 `/api/dashboard/summary`。

- [ ] **Step 6: 前端时间筛选**

  `operate-logs.html` 加时间范围选择器，传 `startTime`/`endTime` 到 `/api/operate-logs/list`。

- [ ] **Step 7: 测试**

  新增：游标查询翻页正确性；queryLogs 时间范围过滤；getRecentLogs 无字符串拼接。

  ```bash
  mvn -pl admin-server -am clean test -Dtest=OperateLogServiceTest
  ```

---

#### Task D2: computeIfAbsent 死锁回归测试 + 删 TODO（决议第 16 题）

**Files:** `ThreadPoolManager.java`, `ThreadPoolManagerReentrancyTest.java`（CREATE）

- [ ] **Step 1: 回归测试**

  新增 `ThreadPoolManagerReentrancyTest`：在 `computeIfAbsent` 的 mapping function 内调 `poolRegistry.values()` / 递归 `computeIfAbsent` 另一个 key，验证抛 `IllegalStateException`（JDK 9+ 禁止 mapping function 修改 map）。

- [ ] **Step 2: 删 TODO + 改注释**

  `ThreadPoolManager.java` 第 71-73 行 TODO 删除，注释改为确定性陈述：「事件/监听器通知必须在 compute lambda 之外执行——lambda 内触发回调（读 poolRegistry.values() 或递归 computeIfAbsent）会抛 IllegalStateException（JDK 9+ 禁止 mapping function 修改 map），见 CONTEXT.md §运行时事件 + ThreadPoolManagerReentrancyTest。」

- [ ] **Step 3: 测试**

  ```bash
  mvn -pl client-sdk -am clean test -Dtest=ThreadPoolManagerReentrancyTest
  ```

---

#### Task D3: ResizableQueue.take 注释（决议第 18 题）

**Files:** `ResizableCapacityLinkedBlockingQueue.java`

- [ ] **Step 1: 补注释**

  `take()` 第 259 行 `if (c == capacity)` 上方补注释：「`capacity` 为 take 时刻的 volatile 快照，`setCapacity` 缩容后此处可能多 signal 一次（虚假唤醒），被唤醒的 put 会重新检查 `count.get() == capacity` 条件，无正确性影响。」

---

## 验收清单

每个 batch 完成后：

- [ ] `mvn -pl <module> -am clean test` 全绿
- [ ] 改动文件与 File Structure 清单一致
- [ ] 相关 ADR 的"后果"节均已落地
- [ ] CONTEXT.md 无需再改（本轮已更新）

全部完成后：

- [ ] `mvn clean install` 全模块构建通过
- [ ] 18 条决议逐条核对实现
- [ ] ADR-0007/0008/0009 的 Status 确认（已 Accepted，实现后可补 Implemented 标记）
- [ ] 提交 PR，PR 描述引用本计划 + 3 份 ADR

---

## 风险与回滚

- **Batch A 的指标口径改动**（error 下沉 wrap）会改变现有 dashboard 的 error/completed 数值语义——上线前需确认 dashboard 告警阈值是否需调整（error 会上升、completed 口径不变）。
- **Batch B 的 API Key HMAC 改动**使存量 key 失效——上线前通知所有接入方重新 createApp。
- **Batch C 的 cache-aside afterCommit** 改变了缓存写入时机，可能有短暂的缓存 miss 增多（提交前读 miss）——监控缓存命中率，确认无异常下跌。
- **Batch D 的 schema 改动**（operate_log 加索引）在 db profile 需手动迁移，local profile 自动生效。

每个 batch 独立提交，便于单独回滚。
