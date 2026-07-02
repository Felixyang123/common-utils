# 动态线程池管理组件

`common-thread-pool` — 一个动态线程池管理组件，提供**客户端 SDK + 管理服务端**的完整解决方案。支持两种模式：**LOCAL**（纯本地配置）和 **CS**（客户端-服务端，配置由 admin-server 集中下发，支持长轮询实时推送）。

> **JDK 要求：21** | **Maven：3.9+** | **Spring Boot 3.x**

---

## 模块架构

| 模块 | 坐标 | 职责 |
|------|------|------|
| `core` | `common-thread-pool-core` | 领域模型：`ThreadPoolConfig`、`ThreadPoolStats`、枚举（`QueueType`/`RejectPolicyType`） |
| `client-sdk` | `common-thread-pool-client` | 客户端：注解、AOP 切面、线程池管理器、远程配置监听、统计上报、Micrometer 指标、自动配置 |
| `admin-server` | `common-thread-pool-admin-server` | 管理服务端：REST 控制器、MyBatis-Plus DAO、可切换存储后端、API 密钥认证、长轮询订阅 |
| `thread-pool-spring-boot-starter` | `thread-pool-spring-boot-starter` | 纯聚合 Starter：传递依赖 client-sdk |

**依赖链：** starter → client-sdk → core（admin-server 独立依赖 core）

---

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.lezai</groupId>
    <artifactId>thread-pool-spring-boot-starter</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

加上依赖即自动生效（基于 Spring Boot `AutoConfiguration.imports` 自动装配），**无需 `@EnableThreadPool` 注解**。如需关闭，配置 `thread.pool.enabled=false`。

### 2. 配置（LOCAL 模式，默认）

```yaml
thread:
  pool:
    enabled: true              # 默认 true，加依赖即生效；置 false 关闭组件
    pools:                     # 自定义线程池列表
      - name: order-pool
        core-pool-size: 20
        maximum-pool-size: 50
        queue-capacity: 2000
        keep-alive-time: 60     # 秒
        queue-type: BLOCKING_QUEUE
        reject-policy-type: ABORT
        allow-core-thread-timeout: false
        thread-name-prefix: order
        daemon: false
      - name: notification-pool
        core-pool-size: 5
        maximum-pool-size: 10
        queue-capacity: 500
```

> 启动时会自动注册一个名为 **`default-pool`** 的默认线程池（核心数=CPU 核数、最大数=2×CPU 核数、队列容量 1024）。`@AsyncThreadPool` 不指定池名时使用它。

### 3. 使用方式

#### 声明式注解（推荐）

```java
@Service
public class OrderService {

    /** 使用默认线程池（default-pool）异步执行 */
    @AsyncThreadPool
    public CompletableFuture<String> processOrder(String orderId) { ... }

    /** 使用指定线程池（必须已声明，否则抛 PoolNotFoundException） */
    @AsyncThreadPool(poolName = "order-pool")
    public CompletableFuture<String> processOrderAsync(String orderId) { ... }

    /** 声明式创建并使用线程池 */
    @CreateThreadPool(
        poolName = "report-pool",
        corePoolSize = 5,
        maximumPoolSize = 10,
        queueCapacity = 100
    )
    public CompletableFuture<String> generateReport(String reportId) { ... }
}
```

> **fail-fast 语义**：`@AsyncThreadPool(poolName = "x")` 引用一个**未声明**的池名（拼写错误、未在 `pools[]` 配置、CS 模式下服务端无此池）会立即抛出 `PoolNotFoundException`，而非静默兜底到默认池。池只能通过以下渠道产生：`pools[]` 配置、`@CreateThreadPool`、服务端下发、编程式 `registerPool`。

#### 编程式

```java
@Autowired
private ThreadPoolManager threadPoolManager;

// 获取已声明的池（不存在抛 PoolNotFoundException）
DynamicThreadPoolWrapper pool = threadPoolManager.getRequiredPool("order-pool");
pool.submit(() -> { /* 业务逻辑 */ });

// 获取池（不存在返回 null，不自动创建）
DynamicThreadPoolWrapper p = threadPoolManager.getPool("order-pool");
```

---

## 运行模式

| 模式 | 触发条件 | 说明 |
|------|----------|------|
| LOCAL | `thread.pool.remote.enabled=false`（默认） | 配置完全来自本地 `pools[]`，不与服务端交互 |
| CS | `thread.pool.remote.enabled=true` | 连接 admin-server，配置可集中下发；长轮询实时推送 + 短轮询补偿 |

---

## 管理服务端（Admin Server）

CS 模式需要一个管理服务端，提供线程池配置的集中管理和推送。

### 启动服务端

服务端是独立进程，配置树为 `threadpool.admin.*`（与客户端 `thread.pool.*` 完全独立）：

```yaml
server:
  port: 8080

threadpool:
  admin:
    storage:
      type: local                  # local（默认，本地 JSON 文件）或 redis-mysql
    auth-enabled: true             # 是否开启 Open API 密钥认证（客户端 SDK 使用）
    history-max-size: 100          # 变更历史最大条数
    api-key-storage-path: ./data/api-keys
    config-storage-path: ./data/configs
    stats-storage-path: ./data/stats
    auth:                          # 管理后台账号密码认证（/api/** 使用，与 Open API 密钥体系独立）
      enabled: true
      username: admin
      password: changeme           # 生产环境务必修改
      secret: <32字节以上的 JWT 签名密钥，生产环境务必修改>
      token-expire-minutes: 30
```

### 存储后端

| 存储类型 | 配置 | 后端 | 特点 |
|----------|------|------|------|
| 本地文件 | `storage.type: local` | JSON 文件（`./data/`） | 零依赖，开箱即用 |
| Redis + MySQL | `storage.type: redis-mysql` | Redisson RMap + MyBatis-Plus | 分布式、持久化，需 MySQL + Redis |

local-file 模式下管理员为单账号（从上述配置读取）；redis-mysql 模式下首次启动会自动在 `admin_user` 表创建该默认账号，后续可在库中管理多个账号。

### 管理员登录

管理后台接口（`/api/**`，不含 `/api/auth/**`）使用账号密码 + JWT 认证，与 Open API 的 `X-App-Id` + `X-API-Key` 体系相互独立：

```bash
POST /api/auth/login
{"username": "admin", "password": "changeme"}
# → { "code": 0, "data": { "token": "...", "username": "admin", "expiresInSeconds": 1800 } }
```

后续管理接口带 `Authorization: Bearer <token>` 请求头。

### API 密钥管理端点

```bash
POST   /api/api-keys                           # 创建密钥（返回明文，仅此一次）
GET    /api/api-keys                           # 列出所有密钥（脱敏）
GET    /api/api-keys/{appId}                   # 查询密钥信息（脱敏）
PUT    /api/api-keys/{appId}                   # 更新密钥（保留凭证哈希）
DELETE /api/api-keys/{appId}                   # 删除密钥
POST   /api/api-keys/{appId}/regenerate        # 重新生成
GET    /api/api-keys/{appId}/history?limit=10  # 变更历史（删除后仍可查）
```

### 配置管理端点

```bash
GET    /api/thread-pool/configs/{appId}                   # 获取全量配置
GET    /api/thread-pool/configs/{appId}/{poolName}        # 获取单个
POST   /api/thread-pool/configs/{appId}                   # 保存全量
POST   /api/thread-pool/configs/{appId}/{poolName}        # 保存单个
DELETE /api/thread-pool/configs/{appId}/{poolName}        # 删除单个
DELETE /api/thread-pool/configs/{appId}                   # 清空应用配置
GET    /api/thread-pool/configs/{appId}/version           # 获取版本号
GET    /api/thread-pool/configs/{appId}/history?limit=10  # 变更历史
```

### 开放 API（客户端调用）

```bash
POST /open/api/thread-pool/config/{appId}/add                  # 添加单条（存在则原样返回，不覆盖）
POST /open/api/thread-pool/configs/{appId}/add                 # 批量添加
GET  /open/api/thread-pool/config/{appId}/pull?version=N       # 短轮询拉取（version 可选）
GET  /open/api/thread-pool/configs/{appId}/subscribe?version=N # 长轮询订阅
POST /open/api/thread-pool/stats/report                        # 统计上报
```

> **长轮询订阅**：客户端调用 `subscribe` 接口并携带当前版本号，服务端通过 `DeferredResult` 挂起请求，配置变更时即时推送（超时返回无变更，客户端立即重新订阅）。
> **启动拉取**：客户端启动时调用 `pull`（不带 version）拉取最新全量配置；本地通过版本号 + 内容 hash 双重去重，避免无意义的池重建。

### 客户端配置（CS 模式）

```yaml
thread:
  pool:
    enabled: true
    remote:
      enabled: true                       # 开启 CS 客户端模式
      server-url: http://localhost:8080   # admin-server 地址
      app-id: my-app                      # 应用身份标识（必填）
      api-key: your-api-key               # API 密钥（必填）
      long-polling-timeout-ms: 30000      # 长轮询超时
      pull-interval-ms: 0                 # 短轮询补偿间隔，0=禁用（长轮询为主通道）
      backoff-initial-ms: 1000            # 长轮询出错后退避初始间隔
      backoff-max-ms: 30000               # 退避上限
      report-enabled: true                # 是否上报统计
      report-interval-ms: 60000           # 上报间隔
    pools:                                # CS 模式下作为启动引导兜底（见下）
      - name: order-pool
        core-pool-size: 20
        maximum-pool-size: 50
```

> **可用性优先**：CS 模式启动时，客户端先用本地 `pools[]` 在本地建池（保证不差于 LOCAL 模式），同时推送给服务端；随后首拉/长轮询拿到服务端配置后覆盖本地。**服务端启动期不可达时，客户端退化到本地配置仍可运行**。运维在服务端的调优具有运行期权威，客户端重启不会覆盖它（add 语义为"存在即返回、不修改"）。
> 详见 [ADR-0001](docs/adr/0001-cs-mode-availability-first-config.md)。
> `remote.enabled=true` 时 `server-url`/`app-id`/`api-key` 为必填，缺失会在启动期校验失败（fail-fast）。

---

## 可观测性

### Micrometer 指标（推荐）

客户端 classpath 存在 Micrometer（如引入 `spring-boot-starter-actuator`）时，**自动**暴露线程池指标——无 Micrometer 时静默跳过，零侵入。

| 指标 | 类型 | 说明 |
|------|------|------|
| `threadpool.threads.{core,max,active,pool}` | Gauge | 核心/最大/活跃/当前线程数 |
| `threadpool.queue.{size,capacity}` | Gauge | 队列大小/容量 |
| `threadpool.tasks.{completed,submitted,error,rejected}` | Gauge | 已完成/已提交/出错/被拒绝任务数 |
| `threadpool.load.factor` | Gauge | 负载率 = activeCount / maximumPoolSize |

每个指标带 `pool`（池名）和 `app`（appId）tag。池被删除后，其指标返回 sentinel 值 `-1`。

### HTTP 上报（CS 模式）

CS 模式下，客户端定时向 admin-server 上报线程池运行状态（与 Micrometer 并存，服务于集中管理控制台）：

```yaml
thread:
  pool:
    remote:
      report-enabled: true
      report-interval-ms: 60000   # 上报间隔（毫秒）
```

### 编程式获取

```java
ThreadPoolStats stats = threadPoolManager.getPoolStats("order-pool");
stats.getCorePoolSize();       // 核心线程数
stats.getActiveCount();        // 活跃线程数
stats.getQueueSize();          // 队列大小
stats.getRejectedTaskCount();  // 被拒绝任务数
stats.getLoadFactor();         // 负载率
stats.getQueueUsageRate();     // 队列使用率
```

---

## 优雅停机

组件通过 Spring `SmartLifecycle` 编排启停：

- **启动**：初始化线程池（含 CS 模式首拉）→ 启动统计上报。
- **停机**：停止远程配置监听 → 停止统计上报 → **最后**排空线程池。线程池采用两阶段关闭（先对所有池发出 shutdown 信号并发排空，再以共享 30s 截止时间依次等待，超时强制 `shutdownNow`），总阻塞时间约等于最慢的单个池而非所有池之和。

---

## 测试

```bash
# 在 common-thread-pool 目录下
mvn -pl core -am clean test                  # core 领域模型
mvn -pl client-sdk -am clean test            # client-sdk（含 MockWebServer wire 契约测试）
mvn -pl admin-server -am clean test          # admin-server
mvn clean test                               # 全模块
```

测试栈：JUnit 5 + AssertJ + Mockito；HTTP 用 OkHttp `MockWebServer`；装配用 `ApplicationContextRunner`。

---

## 注意事项

1. **模式互斥**：LOCAL / CS 由 `thread.pool.remote.enabled` 切换。
2. **default-pool**：启动自动注册，`@AsyncThreadPool` 不指定名字时使用；可在 `pools[]` 中重新声明覆盖其参数。
3. **fail-fast**：引用未声明的池名抛 `PoolNotFoundException`，不静默兜底。
4. **队列容量修改**需要重新创建线程池（运行时仅支持调整核心/最大线程数、存活时间、拒绝策略）。
5. **MapStruct**：admin-server 转换器需 IDE 开启注解处理，或通过 `mvn compile` 生成实现。
6. **长轮询超时**建议设置 30-60 秒。
