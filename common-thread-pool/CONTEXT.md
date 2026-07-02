# common-thread-pool · 领域术语表（CONTEXT）

> 本文件是**词汇表**，只定义概念与边界，不含实现细节、不作 spec 或草稿。
> 配套设计：`docs/superpowers/specs/2026-06-22-common-thread-pool-production-hardening-design.md`

## 运行模式（Operating Mode）

- **LOCAL 模式**：线程池配置完全来自客户端本地（`thread.pool.pools[]`），不与 admin-server 交互。
- **CS 模式（Client-Server）**：客户端与 admin-server 交互，配置可由服务端集中下发；通过 `thread.pool.remote.server.enabled=true` 开启。

## 线程池的产生

- **声明式线程池（Declared Pool）**：通过**受认可的声明渠道**产生的线程池。一个池"存在"的前提是它被声明过。
  - 受认可的声明渠道：`@CreateThreadPool` 注解、`thread.pool.pools[]` 配置、服务端下发（CS 模式）、编程式 `registerPool`。
- **default-pool（默认池）**：单一兜底池。`@AsyncThreadPool` 不指定 `poolName` 时使用它。在初始化阶段被显式注册一次。

## 池的访问规则

- **先声明，后使用（Declared-before-use）**：访问一个未经声明的池名（通常是拼写错误）必须**显式失败**（抛 `PoolNotFoundException`），而非静默兜底创建。
  - `getRequiredPool(name)`：命中不到即抛错——这是 `@AsyncThreadPool` 的取数语义。
  - **取数（访问）不再产生池**：`getPool` 此前"按需自动建池"、以及 CS 模式下"访问即向服务端自动注册新池"的副作用，均被视为缺陷而移除。池只能来自上述声明渠道。

## 配置来源与优先级（CS 模式）

- **启动引导兜底（Bootstrap Fallback）**：CS 模式启动时，客户端先用本地 `thread.pool.pools[]` 在**本地**建池（保证不差于 LOCAL 模式），同时推送给服务端；随后首拉/长轮询拿到服务端配置后**覆盖**本地。
- **优先级**：服务端配置 > 本地配置；但本地配置是"兜底地板"——服务端不可达时退化到本地仍可运行。
- **取舍立场**：**可用性优先于强一致**。服务端启动期不可达不应导致客户端业务线程池缺失。

## 配置所有权（CS 模式）

- **客户端声明初始值，服务端为运行期权威**：客户端用本地 `pools[]` 声明池的初始形态并推送给服务端；运维可在服务端调优。
- **重推不覆盖（Non-destructive re-declare）**：客户端每次启动重推已存在的池**不得覆盖**服务端被调过的值（add 语义为"存在即返回，不修改"）。运行期调优的权威在服务端，客户端重启不回滚它。

## 任务计数术语（指标口径）

> 这些词此前在代码里被混用，统一定义如下，作为可观测指标的口径。

- **submitted（已提交）**：任务被交给线程池的次数（`execute`/`submit` 调用）。**注意**：现有 `submittedTaskCount` 实际在 `beforeExecute` 自增，统计的是 *started*，属命名错误，需正名。
- **started（已开始）**：任务真正开始执行（进入工作线程）。
- **completed（已完成）**：任务执行结束（无论成功失败）。
- **error（出错）**：任务异常完成。其可见性必须在能观察到 `CompletableFuture` 异常完成的层捕获，而非依赖 `ThreadPoolExecutor.afterExecute` 的 throwable 参数（异步提交路径下该参数恒为 null）。
- **rejected（被拒绝）**：任务因池/队列饱和被拒绝策略处理的次数（当前无计数，需补）。
## 部署形态与身份（CS 模式）

- **admin-server（服务端）**：集中管理配置的独立进程，配置树为 `threadpool.admin.*`，与客户端配置完全独立。
- **客户端（client-sdk）**：嵌入业务应用、连接 admin-server 的运行时。配置树为 `thread.pool.remote.*`，描述"客户端如何连服务端"。
- **客户端身份**：CS 模式下，客户端以 `app-id` + `api-key` 标识自己，向 admin-server 认证。启用 CS 客户端模式（`thread.pool.remote.enabled=true`）时身份信息必填，启动期 fail-fast。
- **管理员（Admin User）**：操作 admin-server 管理后台的人员身份。通过账号密码认证获取 JWT token，与客户端的 `app-id` + `api-key` 体系互相独立。管理员为全局角色，不做 appId 级别隔离。
- **配置语义边界**：`thread.pool.remote.*` 是"客户端连服务端"的连接信息（如 server-url），**不是**"服务端自身"的配置。两者（`thread.pool.remote.*` vs `threadpool.admin.*`）归属不同进程，不得混用。

## 订阅通知协议（CS 模式）

- **变更通知（ConfigChangeNotification）**：subscribe 接口返回的轻量信号，仅含 `{appId, version}`。客户端收到后主动 pull 获取全量配置。设计意图：避免服务端高频变更时直接推送全量配置导致脏写客户端缓存。
