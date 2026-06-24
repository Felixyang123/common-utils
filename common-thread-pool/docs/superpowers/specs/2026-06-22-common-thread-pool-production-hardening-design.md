# common-thread-pool 生产化加固 · 路线图（Roadmap Spec）

> 文档类型：**路线图规格**（只定四阶段顺序、目标、问题清单、依赖；各阶段实现细节下沉到阶段专属 spec）
> 编写日期：2026-06-22
> 模块根：`D:/code/common-utils/common-thread-pool`
> 包根：`com.lezai.threadpool`
> 兼容性约束：**pre-1.0，允许破坏性变更**（目标为正确的最终形态，不背兼容包袱）
> 配套领域文档：`CONTEXT.md`、`docs/adr/0001-cs-mode-availability-first-config.md`
> 阶段 1 专属 spec：`docs/superpowers/specs/2026-06-22-stage1-runtime-hardening-design.md`

---

## 0. 背景与范围

`common-thread-pool` 是动态线程池管理组件（客户端 SDK + 管理服务端）。`admin-server` 已在上一轮完成分层重构。本次聚焦其余三模块，以生产实践标准完善、优化、重构。

- **纳入**：`core`、`client-sdk`、`thread-pool-spring-boot-starter`、孤儿外层 `common-utils/thread-pool-spring-boot-starter`。
- **不纳入**：`admin-server`（已重构）；`samples` 仅随 starter 整合做最小适配。

### 设计原则
1. **TDD 贯穿**：每阶段先补特征测试锁行为，再改；测试缺口（P7）分散在各阶段消解。
2. **fail-fast 取代 fail-silent**：错配 / 未知池名显式报错，而非静默兜底。
3. **生命周期闭环**：凡 `start()` 必有对称 `stop()`；持有资源必随 Spring 上下文关闭释放。
4. **单一事实源**：自动配置、注解、属性各只保留一份。

---

## 1. 已验证的核心问题（P1–P8）

均已在源码中亲自核对行号。

| 编号 | 问题 | 位置 | 阶段 |
|---|---|---|---|
| **P1** | `detector.stop()` 不关 `pullConfigsScheduler`、不关 `httpClient` | `RemoteConfigSourceDetector:84-90` | 1 |
| **P2** | 长轮询异常 `Thread.sleep(10)`，无退避/上限/熔断 | `RemoteConfigSourceDetector:122` | 1 |
| **P3** | 关停从不调 `threadPoolManager.shutdown()`；`shutdown()` 异步关停后立即 `clear()`，靠永不关闭的 static daemon 池兜底 | `ThreadPoolAutoConfiguration:110-116,130-133`、`ThreadPoolManager:158-160,207-213,227-241` | 1 |
| **P4** | `getPool(未知名)` 静默建池；CS 模式还自动向服务端注册、服务端宕机时 NPE | `ThreadPoolManager:68-70`、`RemoteConfigSourcePoolManager:21-27`、`DynamicThreadPoolWrapper:32` | 1 |
| **P5** | `pullIntervalMs` 默认 0 → 空转；配置无校验；`remote.server.*` 语义混乱、默认端口 8088 错 | `ThreadPoolProperties:141` 等全类 | 1 |
| **P6** | 两份重复 `ThreadPoolAutoConfiguration`+`@EnableThreadPool`；外层 starter 孤儿（根 pom 不收录、不构建、spring.factories 指错包、依赖父 POM） | client-sdk vs starter 双包；外层 starter 模块 | 2 |
| **P7** | 18 个生产类中 15 个零测试；CS 客户端↔服务端 wire contract 从无端到端测试 | client-sdk / core / starter | 1–4 |
| **P8** | README 描述 `mode/default-pool/getInstance` 等代码中不存在的 API | README vs 代码 | 4 |

### grill 评审新增的已确认问题（归入阶段 1）
| 编号 | 问题 | 位置 |
|---|---|---|
| **P9** | 客户端 pull 打 `/configs/.../pull`（复数），服务端只暴露 `/config/.../pull`（单数）→ 首拉 404；客户端 `deleteConfig` 走 `/api/...` 管理端点而非 `/open/api/...` | `RemoteConfigSourceDetector:135,300` vs `OpenThreadPoolConfigController:70` |
| **P10** | `errorTaskCount` 依赖 `afterExecute` 的 `t`，但异步提交路径下该参数恒 null → error 指标恒为 0（谎报） | `DynamicThreadPoolWrapper:88-94` + `ThreadPoolAspect:49` |
| **P11** | 非 Future 的 `@AsyncThreadPool` 创建 future 后丢弃、返回 null，异常被静默吞 | `ThreadPoolAspect:57-61` |
| **P12** | 启动副作用（同步首拉、起订阅线程、reporter.start）在 `@Bean` 构造期 eager 执行 | `ThreadPoolAutoConfiguration:104,126` |

---

## 2. 阶段顺序与依赖（方案 A：风险优先，严格串行）

```
阶段1 运行时加固 (P1-P5, P9-P12，补相关测试 + CS 契约测试)   ← 地基，先行
   └─► 阶段2 Starter 整合 (P6)        ← 依赖阶段1把生命周期修在 client-sdk 那份 auto-config 上
          └─► 阶段3 可观测性 (Micrometer，与 HTTP 上报共存)
                 └─► 阶段4 文档准确性 (P8，按最终形态重写)
```

每阶段独立 commit、可独立交付；前一阶段测试全绿方进入下一阶段。

---

## 3. 各阶段目标（细节见阶段专属 spec）

### 阶段 1 — 运行时加固
生命周期闭环（SmartLifecycle 编排、资源释放、优雅停机）、长轮询指数退避、fail-fast（先声明后使用 + 删除 CS 自动注册副作用）、CS 模式启动引导兜底（可用性优先，见 ADR-0001）、配置重构与校验、CS 真实契约测试 + 修 wire bug、错误计数与异常可见性修复。
> **详见**：`2026-06-22-stage1-runtime-hardening-design.md`

### 阶段 2 — Starter 整合
收敛为唯一 `AutoConfiguration.imports` 自动装配；删除 starter 内重复 auto-config/注解、client-sdk 的 `@EnableThreadPool`、**物理删除**外层孤儿 starter 目录（外层根 pom 不动）；samples 去注解靠自动装配；`thread.pool.enabled` 默认开（opt-out）。

### 阶段 3 — 可观测性
Micrometer 自定义 `MeterBinder`（可选依赖，与 HTTP 上报共存）；指标生命周期以快照兜底为主、`createPool` 钩子为辅（避开 CS 子类继承覆盖绕过）；补 `rejectedCount`；error 指标在 CF 完成层捕获（阶段 1 已修）。

### 阶段 4 — 文档准确性
按最终形态重写 README 与 samples 文档：删 `mode/default-pool/getInstance`；按真实 `thread.pool.*` / `thread.pool.remote.*` 属性树写；补 fail-fast 行为、Micrometer 指标、优雅停机说明。

---

## 4. 全局测试策略
- **框架**：JUnit 5 + AssertJ + Mockito；HTTP 用 OkHttp `MockWebServer`；装配用 `ApplicationContextRunner`；CS 契约用真实同进程 admin-server + client-sdk 走 HTTP。
- **门槛**：被触及类覆盖率 ≥ 80%；核心并发/生命周期/退避/契约路径必测。

---

## 5. 风险与回滚
- 破坏性变更（fail-fast、删 `@EnableThreadPool`、删外层 starter、`remote` 属性改名）集中阶段 1-2，pre-1.0 可接受。
- 回滚：未提交 `git restore <path>`；已提交 `git revert <commit>`。

---

## 附录 A：验证命令速查
```sh
# 在 common-thread-pool 目录下
mvn -pl core -am clean test
mvn -pl client-sdk -am clean test
mvn -pl thread-pool-spring-boot-starter -am clean test
mvn clean test
```
