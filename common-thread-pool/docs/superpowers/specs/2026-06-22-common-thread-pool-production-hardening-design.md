# common-thread-pool 生产化加固 · 设计文档

> 文档类型：设计规格（Spec）
> 编写日期：2026-06-22
> 模块根：`D:/code/common-utils/common-thread-pool`
> 包根：`com.lezai.threadpool`
> 兼容性约束：**pre-1.0，允许破坏性变更**（目标为正确的最终形态，不背兼容包袱）
> 配套：本 spec 为**全量分阶段路线图**；每个阶段后续各自展开为独立实现计划（plan）。

---

## 0. 背景与目标

`common-thread-pool` 是一个动态线程池管理组件，提供「客户端 SDK + 管理服务端」方案。`admin-server` 已在上一轮完成分层重构，本次聚焦其余三个模块（`core` / `client-sdk` / `thread-pool-spring-boot-starter`，含本模块**之外**的那个坏掉的外层 starter），以**生产实践标准**完善、优化、重构。

**总目标**：消除运行时资源泄漏与非优雅停机、用 fail-fast 取代静默兜底、收敛重复的自动装配、补齐可观测性与测试、让文档与代码一致。

### 范围边界
- **纳入**：`core`、`client-sdk`、`thread-pool-spring-boot-starter`、外层 `common-utils/thread-pool-spring-boot-starter`。
- **不纳入**：`admin-server`（已重构）；`samples` 仅随 starter 整合做最小适配。

### 设计原则
1. **TDD 贯穿**：每阶段先补特征测试锁定行为，再改；测试缺口（P7）分散在各阶段消解，不单列阶段。
2. **fail-fast 取代 fail-silent**：错配 / 未知池名显式报错，而非静默兜底。
3. **生命周期闭环**：凡 `start()` 必有对称 `stop()`；凡持有资源必随 Spring 上下文关闭而释放。
4. **单一事实源**：自动配置、注解、属性各只保留一份。

---

## 1. 已验证的核心问题

下列问题均已在源码中**亲自核对行号**，非二手报告。

| 编号 | 问题 | 位置 | 影响 |
|---|---|---|---|
| **P1** | `detector.stop()` 仅 `interrupt()` 订阅线程，**不关 `pullConfigsScheduler`、不关 `httpClient`** | `RemoteConfigSourceDetector:84-90`（调度器 `:62`、httpClient `:52`） | 每次应用重启泄漏 1 个调度线程 + 1 个 OkHttp 连接池/分发线程 |
| **P2** | 长轮询异常分支 `Thread.sleep(10)`，**无退避、无上限、无熔断** | `RemoteConfigSourceDetector:122` | 鉴权失败 / 4xx 即陷入约 100 次/秒的热自旋 |
| **P3** | 关停链路**从不调用** `threadPoolManager.shutdown()`；且 `shutdown()` 提交异步关停后**立即 `clear()`**，仅靠永不关闭的 static daemon 池兜底 | `ThreadPoolAutoConfiguration:110-116,130-133`、`ThreadPoolManager:158-160,207-213,227-241` | 停机丢弃在途任务，非优雅；static 池泄漏 |
| **P4** | `getPool(未知名)` 用 `computeIfAbsent` **静默创建默认池** | `ThreadPoolManager:68-70` | `@AsyncThreadPool(poolName=...)` 池名写错时悄悄跑进错误的池，无任何报错 |
| **P5** | `pullIntervalMs` 默认 `0` → `scheduleAtFixedRate(..,0,0,..)` 高频空转；`apiKey` 等无任何校验 | `ThreadPoolProperties:141`；全类无 `@Validated` | CPU 空耗；静默错配（空 API Key 被服务端拒绝而难以察觉） |
| **P6** | 两份近乎重复的 `ThreadPoolAutoConfiguration` + 两份 `@EnableThreadPool`；外层 starter 依赖父 POM（packaging=pom），其 `spring.factories` 指向不存在的类名 | client-sdk `config.*` vs starter `starter.config.*`；外层 starter 模块 | 改一处漏一处；外层 starter 实际不可自动装配 |
| **P7** | 18 个生产类中 15 个零测试（`RemoteConfigSourceDetector`、`ThreadPoolStatsReporter`、`ThreadPoolAspect`、`CreateThreadPoolAspect`、`ThreadPoolInitializer`、`RemoteConfigSourcePoolManager`、`ThreadPoolAutoConfiguration`、`core` 中 6/7 类等） | client-sdk / core / starter | 无回归防线 |
| **P8** | README 描述 `mode: LOCAL/FILE/CS/NACOS`、`default-pool` 配置块、`ThreadPoolManager.getInstance()` 等**代码中不存在**的 API | README vs 代码 | 公开契约误导 |

### 目标形态（收敛后）
```
core            领域模型 + Jakarta 校验（行为不变，补测试）
client-sdk      运行时：池管理、AOP、远程配置、统计上报、可观测性
                —— 唯一的 ThreadPoolAutoConfiguration 位于此（autoconfigure 角色）
thread-pool-spring-boot-starter
                唯一 starter：纯聚合 POM（无代码），仅依赖传递 client-sdk
                —— 删除 starter 内重复 auto-config / 注解、外层坏 starter 模块
```

### 阶段顺序与依赖（方案 A：风险优先，严格串行）
```
阶段1 运行时加固 (P1-P5，补相关测试)   ← 地基，先行
   └─► 阶段2 Starter 整合 (P6)          ← 依赖阶段1把生命周期修在 client-sdk 那份 auto-config 上
          └─► 阶段3 可观测性 (Micrometer，与 HTTP 上报共存)
                 └─► 阶段4 文档准确性 (P8，按最终形态重写)
```
每阶段独立 commit、可独立交付；前一阶段测试全绿方进入下一阶段。

---

## 2. 阶段 1 — 运行时加固（核心）

按「修复簇」组织。

### 簇 A：生命周期闭环（P1 + P3）
**问题本质**：`detector`、`reporter`、`ThreadPoolManager` 三个资源持有者的释放路径都不完整，且关停**无序**——当前是两个分散的 `ApplicationListener<ContextClosedEvent>`，还硬依赖可能为 `null` 的 reporter bean。

**设计决策**：
1. 改用 **`SmartLifecycle`** 统一编排启停顺序：
   - `detector` / `reporter` → **高 phase**（后启动、先停止）：停机时先切断外部输入与上报。
   - 线程池关闭 → **低 phase**（后停止）：等外部输入停了，再放干在途任务。
2. **`detector.stop()` 补全释放**：`pullConfigsScheduler.shutdown()` + `awaitTermination`；关闭 OkHttp（`dispatcher().executorService().shutdown()` + `connectionPool().evictAll()`）。`reporter.stop()` 同样补关 httpClient。
3. **`ThreadPoolManager` 优雅停机重写**：
   - 删除同名内部类 `CompletableFuture`（与 `java.util.concurrent.CompletableFuture` 混淆）及其永不关闭的 static daemon 池。
   - `shutdown()` 改为**同步、有界等待**：逐池 `shutdown()` → `awaitTermination(可配置超时)` → 超时则 `shutdownNow()`。移除「异步关停后立即 `clear()`」的竞态。
   - 接入 Spring 生命周期（当前完全未接）。

### 簇 B：长轮询退避（P2）
- `subscribeWithLongPolling` 异常分支：`Thread.sleep(10)` → **指数退避**（初始 1s → ×2 → 封顶 30s），成功后重置退避。
- **区分语义**：正常超时 / 无变更（`analyzeResponse` 返回 `null` 但 HTTP 成功）≠ 异常。仅真异常才退避；正常超时立即重订阅。
- 退避初始 / 上限值走配置（默认值合理）。

### 簇 C：fail-fast 取代静默兜底（P4）
- 拆分 `getPool` 语义：
  - 新增 **`getRequiredPool(name)`**：池不存在即抛 `PoolNotFoundException`（新增，继承 `RuntimeException`）。`ThreadPoolAspect`（`@AsyncThreadPool`）改用它——池名写错立即报错。
  - 显式创建仍走 `registerPool` / `@CreateThreadPool`。
- **default-pool 显式化**：初始化时注册一次默认池；`@AsyncThreadPool` 不指定名字时引用它，而非每次兜底创建。
- **破坏性影响**：此前「错误池名也能跑」现在会抛错——pre-1.0 + fail-fast 原则下为期望行为。

### 簇 D：配置校验（P5）
- `ThreadPoolProperties` 加 `@Validated` + 字段级约束：`@Min` 线程数 / 队列容量；`@Positive` 间隔；远程启用时 `apiKey` `@NotBlank`；跨字段 `maximumPoolSize >= corePoolSize`（`@AssertTrue` 校验方法）。
- **`pullIntervalMs` 语义修正**：默认 `0` 改为「**0 = 禁用短轮询补偿**」；`start()` 内 `if (pullIntervalMs > 0)` 才调度。长轮询为主通道，短轮询为补偿，0 不再意味着空转。
- 校验失败 → Spring 启动期 fail-fast。

### 簇 E：测试（P7 本阶段相关部分）
- `RemoteConfigSourceDetector`、`ThreadPoolStatsReporter`：OkHttp `MockWebServer` 测拉取 / 长轮询 / 退避 / **关停后资源确实释放**。
- `ThreadPoolManager`：测 fail-fast、优雅 `shutdown` 真正阻塞等待、并发更新原子性。
- 生命周期：`ApplicationContextRunner` 验证 start→stop 全链路、本地 / 远程模式 bean 装配正确。
- 门槛：本阶段触及类覆盖率 ≥ 80%。

---

## 3. 阶段 2 — Starter 整合（P6）

### 目标形态（标准 Spring Boot 自动装配）
```
client-sdk = autoconfigure 角色
  ├─ 唯一的 ThreadPoolAutoConfiguration（阶段1加固后的那份）
  └─ 新增 META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
        → 指向该类（放 classpath 即自动装配，由 thread.pool.enabled 总开关控制）

thread-pool-spring-boot-starter = 纯聚合 POM（无代码）
  └─ 仅依赖 client-sdk（client-sdk 再传递 core）

消费方：仅加 starter 依赖即生效；thread.pool.enabled=false 可关闭；无需 @EnableThreadPool
```

### 启用开关决策
- 删除 `@EnableThreadPool`，**改用纯 starter 自动装配**。
- 启用 / 禁用由**既有属性** `thread.pool.enabled` 控制（`ThreadPoolAutoConfiguration` 上已有 `@ConditionalOnBooleanProperty(name="thread.pool.enabled", matchIfMissing=true)`），**默认开（opt-out）**——符合 starter「加依赖即生效」的心智模型。无需新增 condition。
- 理由：`@EnableXxx` 是 Spring Framework 特性的激活模型，不是 starter 的；保留它会与 `AutoConfiguration.imports` 形成两套激活路径，正是当前两份 auto-config 混乱的根源。

### 删除清单（pre-1.0，破坏性允许）
- starter 内重复的 `starter.config.ThreadPoolAutoConfiguration` + `starter.annotation.EnableThreadPool`。
- client-sdk 的 `annotation.EnableThreadPool`（改纯自动装配后多余）。
- **整个外层 `common-utils/thread-pool-spring-boot-starter` 模块** + 外层根 `pom.xml` 中对应的 `<module>` 声明。

### 适配
- `samples/sample-local`、`samples/sample-cs-client`：移除 `@EnableThreadPool`，依赖切换为整合后的 starter，靠自动装配生效。

### 测试（阶段 2）
- `ApplicationContextRunner`：仅加 starter → 关键 bean 装配成功；`thread.pool.enabled=false` → 不装配；远程 / 本地两模式各验证一次。

---

## 4. 阶段 3 — 可观测性（Micrometer，与 HTTP 上报共存）

### 定位
两条独立通道，职责不同，并存：
- **HTTP 上报**（`ThreadPoolStatsReporter`，阶段 1 已加固）→ 推送给 admin-server，服务于集中管理控制台。保留不动。
- **Micrometer 指标**（新增）→ 暴露给 Prometheus / Grafana 等，服务于标准生产可观测。

### 设计决策
1. **可选依赖，零侵入**：`micrometer-core` 设为 `optional`；`@ConditionalOnClass(MeterRegistry.class)` + `@ConditionalOnBean(MeterRegistry.class)` 装配。消费方有 Micrometer 才启用，无则静默跳过。
2. **集成方式：自定义 `MeterBinder`**。理由：组件卖点正是 `loadFactor`、`queueUsageRate`、`errorTaskCount` 等自定义指标，Micrometer 内置 `ExecutorServiceMetrics` 拿不到这些；自定义 binder 直接映射 `DynamicThreadPoolWrapper.getStats()`。
3. **动态池的指标生命周期**：池**注册时绑定** meters、**删除时移除** meters——挂在 `ThreadPoolManager` 的 register / remove 钩子上，避免指标泄漏或残留。
4. **统一标签**：每个 meter 带 `pool`（池名）+ `app`（appId）tag。
5. **补拒绝计数**：当前拒绝策略（含 `BlockedPolicy`）无计数。新增 `rejectedCount` 到 `DynamicThreadPoolWrapper`，同时服务可观测与补齐拒绝可见性。

### 指标清单（`threadpool.*` 前缀，遵循 Micrometer 命名惯例）
| 指标 | 类型 | 来源 |
|---|---|---|
| `threadpool.threads{type=core/max/active/pool}` | Gauge | wrapper 实时 |
| `threadpool.queue.size` / `threadpool.queue.capacity` / `threadpool.queue.usage` | Gauge | wrapper |
| `threadpool.tasks.completed` / `.error` / `.rejected` | Gauge/Counter | wrapper 计数 |
| `threadpool.load.factor` | Gauge | wrapper |

### 测试（阶段 3）
- `SimpleMeterRegistry`：池注册后指标存在且值正确；池删除后指标确实移除。
- `FilteredClassLoader`：classpath 无 Micrometer 时不装配、不报错。

---

## 5. 阶段 4 — 文档准确性（P8）

按**整合后的最终形态**重写（放最后做，避免被前几阶段改动反复推翻）。

- **删除代码中不存在的描述**：`mode: LOCAL/FILE/CS/NACOS`、`default-pool` 配置块、`ThreadPoolManager.getInstance()` 单例。
- **按真实 API 重写**：实际开关为 `thread.pool.enabled` + `thread.pool.remote.server.enabled`（本地 / 远程双模式）；属性树以 `ThreadPoolProperties` 为准（`thread.pool.pools[]`、`thread.pool.remote.{server,client}.*`）。
- **接入方式更新**：删除 `@EnableThreadPool`，改「加 starter 依赖即生效，`thread.pool.enabled=false` 可关」。
- **补充新增内容**：fail-fast 行为（未知池名抛 `PoolNotFoundException`）、Micrometer 指标清单与开启方式、优雅停机说明。
- 同步 `samples` 的 README / 注释。

---

## 6. 全局测试策略

- **框架**：JUnit 5 + AssertJ + Mockito（沿用现状）；HTTP 用 OkHttp `MockWebServer`；装配用 `ApplicationContextRunner`。
- **分层目标**：`core` 补齐枚举 / 模型 / 校验测试；`client-sdk` 覆盖此前零测试的 8 个类；`starter` 加装配测试。
- **门槛**：被触及的类覆盖率 ≥ 80%；核心并发 / 生命周期 / 退避路径必测。

---

## 7. 交付物清单（最终形态）

**阶段 1**
- 🆕 `exception/PoolNotFoundException`
- ✏️ `RemoteConfigSourceDetector`（补资源释放、指数退避、超时/异常语义区分）
- ✏️ `ThreadPoolStatsReporter`（补 httpClient 释放）
- ✏️ `ThreadPoolManager`（优雅停机重写、删 static `CompletableFuture` 内部类、新增 `getRequiredPool`、default-pool 显式化）
- ✏️ `ThreadPoolProperties`（`@Validated` + 字段/跨字段校验、`pullIntervalMs` 语义修正）
- ✏️ `ThreadPoolAutoConfiguration`（`SmartLifecycle` 编排启停）
- ✏️ `ThreadPoolAspect`（改用 `getRequiredPool`）
- 🆕 上述类的测试

**阶段 2**
- 🆕 client-sdk `META-INF/spring/...AutoConfiguration.imports`
- 🗑️ starter 内 `starter.config.ThreadPoolAutoConfiguration`、`starter.annotation.EnableThreadPool`
- 🗑️ client-sdk `annotation.EnableThreadPool`
- 🗑️ 外层 `common-utils/thread-pool-spring-boot-starter` 模块 + 外层根 POM `<module>` 声明
- ✏️ `thread-pool-spring-boot-starter` → 纯聚合 POM
- ✏️ `samples/*` 适配 + 装配测试

**阶段 3**
- 🆕 `metrics/ThreadPoolMetricsBinder`（可选依赖装配）
- ✏️ `DynamicThreadPoolWrapper`（新增 `rejectedCount`）
- ✏️ `ThreadPoolManager`（register/remove 指标生命周期钩子）
- 🆕 指标测试

**阶段 4**
- ✏️ `common-thread-pool/README.md`、`samples` 文档重写

---

## 8. 风险与回滚

- 每阶段独立 commit、可独立交付；阶段间严格串行，前阶段测试全绿才进下一阶段。
- 破坏性变更（fail-fast 未知池名、删除 `@EnableThreadPool`、删除外层 starter）集中在阶段 1-2，已确认 pre-1.0 可接受。
- 回滚原语：未提交 `git restore <path>`；已提交 `git revert <commit>`。涉及 MapStruct/生成源时回滚后需 `clean compile`（本次三个模块基本不涉及，admin-server 除外）。

---

## 附录 A：验证命令速查

```sh
# 在 common-thread-pool 目录下
mvn -pl core -am clean test                  # core
mvn -pl client-sdk -am clean test            # client-sdk（依赖 core）
mvn -pl thread-pool-spring-boot-starter -am clean test
mvn clean test                               # 全模块
```
