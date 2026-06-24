# 阶段 1 · 运行时加固 · 设计文档

> 文档类型：**阶段专属 spec**（路线图 `2026-06-22-common-thread-pool-production-hardening-design.md` 的阶段 1 展开）
> 编写日期：2026-06-22
> 模块：`client-sdk`（主）、`core`（少量）
> 配套领域文档：`CONTEXT.md`、`docs/adr/0001-cs-mode-availability-first-config.md`
> 后续：本 spec 经用户审阅后，由 writing-plans 展开为实现计划。

---

## 0. 目标与范围

修复 client-sdk 运行时的资源泄漏、非优雅停机、静默兜底、配置混乱与契约错位，并以 fail-fast / 可用性优先原则重构池访问与 CS 模式启动。覆盖路线图问题 **P1–P5、P9–P12**（P7 测试部分同步消解）。P6（starter 整合）属阶段 2，P8（文档）属阶段 4，不在本 spec。

所有破坏性变更在 pre-1.0 约束下可接受。

---

## 1. 修复簇

### 簇 A：生命周期闭环（P1、P3、P12）

**问题**：`detector`/`reporter`/`ThreadPoolManager` 释放路径不完整且无序；启动副作用在 `@Bean` 构造期 eager 执行。

**设计**：
1. 改用 **`SmartLifecycle`** 统一编排启停。
2. **启动副作用全部移入 `start()`**：同步首拉、起订阅线程、`reporter.start()` 不得留在 bean 构造期。
3. **`detector.stop()` 补全释放**：`pullConfigsScheduler.shutdown()` + `awaitTermination`；关闭 OkHttp（`dispatcher().executorService().shutdown()` + `connectionPool().evictAll()`）。`reporter.stop()` 同样补关 httpClient。
4. **`ThreadPoolManager.shutdown()` 优雅停机重写**：
   - 删除同名内部类 `CompletableFuture`（与 JDK 类混淆）及其永不关闭的 static daemon 池。
   - 改为**同步、有界等待**：逐池 `shutdown()` → `awaitTermination(可配置超时)` → 超时 `shutdownNow()`。移除"异步关停后立即 `clear()`"的竞态。
5. **停机顺序**：detector & reporter 先停 → Web 容器排空在途请求 → **池最后排空**（晚于 Web 优雅停机，避免在途请求提交任务时池已关）。
6. **契约边界**：不保证"bean 的 `@PreDestroy` 里向托管池提交新任务"能成功——此反模式显式列为不在保证范围。

### 簇 B：长轮询退避（P2）

**设计**：
- `subscribeWithLongPolling` 异常分支：`Thread.sleep(10)` → **指数退避**（初始 1s → ×2 → 封顶 30s），成功后重置。
- **区分语义**：正常超时/无变更（HTTP 成功但 `analyzeResponse` 返回 null）≠ 异常。仅真异常退避；正常超时立即重订阅。
- 退避初始/上限走配置（默认值合理）。

### 簇 C：fail-fast 与池访问语义（P4）

**设计**（详见 `CONTEXT.md`「池的访问规则」）：
- 确立规则 **「先声明，后使用」**：访问未经声明的池名（拼写错）必须显式失败。
- 新增 **`getRequiredPool(name)`**：命中不到抛 `PoolNotFoundException`（新增，继承 `RuntimeException`）。`ThreadPoolAspect`（`@AsyncThreadPool`）改用它。
- **删除 `getPool` 的自动建池副作用**；**删除 CS 模式 `RemoteConfigSourcePoolManager.getPool` 的"访问即向服务端自动注册"副作用**（服务端宕机时该路径 NPE，见 P4）。池只能来自声明渠道：`@CreateThreadPool`、`thread.pool.pools[]`、服务端下发、编程式 `registerPool`。
- **default-pool 显式化**：初始化阶段注册一次默认池；`@AsyncThreadPool` 不指定名字时引用它。

| 场景 | 目标行为 |
|---|---|
| LOCAL，引用未声明的池（拼写错） | 抛 `PoolNotFoundException` |
| LOCAL/CS，`@AsyncThreadPool` 不写池名 | 用显式 default-pool |
| CS，引用服务端真实存在的池 | 启动期同步首拉已加载 → 命中 |
| CS，引用服务端没有的池（拼写错） | 抛 `PoolNotFoundException`（不再自动注册、不再 NPE） |
| 显式创建池 | 走 `registerPool` / `@CreateThreadPool` / `pools[]` / 服务端推送 |

### 簇 D：CS 模式启动引导兜底（P4 子问题，见 ADR-0001）

**问题**：CS 模式本地池唯一来源是 `detector.start()` 的同步首拉；服务端启动期不可达 → 业务池全缺 → 叠加 fail-fast 即 `PoolNotFoundException`，哪怕池写在 `pools[]` 里。

**设计**（详见 `CONTEXT.md`「配置来源与优先级」「配置所有权」、ADR-0001）：
- CS 模式启动时，**先用本地 `pools[]` 在本地建池**（不差于 LOCAL），同时推送给服务端；首拉/长轮询拿到服务端配置后**覆盖**本地。
- **重推不覆盖**：服务端 add 语义为"存在即返回、不修改"，客户端重启不回滚服务端调优值。
- 服务端不可达 → 退化到本地配置仍可运行。**可用性优先于强一致。**

### 簇 E：配置重构与校验（P5）

**问题**：`remote.server.*` 把"客户端连服务端"的连接信息命名为"server"，`server.enabled` 名不副实，默认端口 8088 错（实际 8080），与服务端 `threadpool.admin.*` 混淆。

**设计**（详见 `CONTEXT.md`「部署形态与身份」）：
- `ThreadPoolProperties.remote` 重构为清晰客户端语义：
  - `thread.pool.remote.enabled`（默认 false）= 启用 CS 客户端模式。
  - `thread.pool.remote.server-url`（默认 `http://localhost:8080`，**修正端口**）。
  - `thread.pool.remote.app-id` / `api-key`。
- `@Validated` + 字段级约束：`@Min` 线程数/容量、`@Positive` 间隔；`remote.enabled=true` 时 `server-url`/`app-id`/`api-key` 必填；跨字段 `maximumPoolSize >= corePoolSize`（`@AssertTrue`）。
- **`pullIntervalMs` 语义修正**：默认 0 = 禁用短轮询补偿（`if (pullIntervalMs > 0)` 才调度）；长轮询为主通道。
- admin-server 的 `threadpool.admin.*` 保持不动。

### 簇 F：CS wire 契约修复 + 真实契约测试（P9）

**已确认的契约 bug**：
- 客户端 pull 打 `/open/api/thread-pool/configs/{appId}/pull`（复数），服务端只有 `/config/{appId}/pull`（单数）→ 首拉 404。**统一为单数**（与服务端一致）。
- 客户端 `deleteConfig` 打 `/api/thread-pool/...`（管理端点），应走 `/open/api/thread-pool/...`（开放端点）。

**测试**：
- 新增 **CS 模式真实端到端契约测试**：同进程拉起真实 admin-server（local 存储）+ 真实 client-sdk，走真实 HTTP 完成 注册 → 首拉建池 → 改配置 → 长轮询收到变更 → 统计上报。锁住 URL/DTO/错误码契约。

### 簇 G：错误计数与异常可见性（P10、P11）

**问题**：`errorTaskCount` 依赖 `afterExecute` 的 `t`，异步提交路径下恒 null → error 指标恒 0；非 Future 的 `@AsyncThreadPool` 创建 future 后丢弃，异常被静默吞。

**设计**（详见 `CONTEXT.md`「任务计数术语」）：
- **错误计数移到 CF 完成层**：切面在 future 上挂 `whenComplete`/`handle` 累加 error，不再依赖 `afterExecute` 的 `t`。
- **非 Future 的 `@AsyncThreadPool` 保留 fire-and-forget 返回语义**（不阻塞），**但异常在 `whenComplete` 记日志 + 计数**，不得静默吞。
- **补 `rejectedCount`**：拒绝策略（含 `BlockedPolicy`）增加计数。
- **命名修正**：`submittedTaskCount`（实为 started）正名为 `startedTaskCount`；`submitted` 口径在真正提交点统计。

### 簇 H：测试（P7 本阶段部分）
- `RemoteConfigSourceDetector`、`ThreadPoolStatsReporter`：`MockWebServer` 测拉取/长轮询/退避/关停后资源释放。
- `ThreadPoolManager`：fail-fast、优雅 shutdown 阻塞等待、并发更新。
- 生命周期：`ApplicationContextRunner` 验证 start→stop 全链路、本地/远程模式装配。
- CS 契约测试见簇 F。
- 门槛：触及类覆盖率 ≥ 80%。

---

## 2. 交付物清单

**新增**
- `exception/PoolNotFoundException`
- CS 模式真实端到端契约测试
- 上述各类的单元/生命周期测试

**修改**
- `RemoteConfigSourceDetector`（资源释放、指数退避、语义区分、pull URL 单数、delete 走 open 端点）
- `ThreadPoolStatsReporter`（补 httpClient 释放）
- `ThreadPoolManager`（优雅停机重写、删 static 内部类、`getRequiredPool`、default-pool 显式化、指标钩子预留）
- `RemoteConfigSourcePoolManager`（删 `getPool` 自动注册副作用、本地兜底建池）
- `ThreadPoolProperties`（`remote` 重构、`@Validated`、`pullIntervalMs` 语义）
- `ThreadPoolAutoConfiguration`（SmartLifecycle 编排、启动副作用入 `start()`、属性名跟随重构）
- `ThreadPoolAspect`（改用 `getRequiredPool`、CF 完成层 error 计数、非 Future 异常记录）
- `DynamicThreadPoolWrapper`（`rejectedCount`、计数命名修正）

---

## 3. 与后续阶段的衔接
- 阶段 2（Starter 整合）依赖本阶段把生命周期修在 client-sdk 的 `ThreadPoolAutoConfiguration` 上；本阶段不删重复 auto-config（留给阶段 2）。
- 阶段 3（Micrometer）的 error/rejected 指标依赖本簇 G 的计数修正；指标生命周期挂钩预留由本阶段在 `createPool` 留出。
