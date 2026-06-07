# 动态线程池管理组件

`common-thread-pool` — 一个支持多种配置模式（LOCAL / FILE / CS / NACOS）的动态线程池管理组件，提供**客户端 SDK + 管理服务端**的完整解决方案。

> **JDK 要求：21** | **Maven：3.9+** | **Spring Boot 3.x**

---

## 模块架构

| 模块 | 坐标 | 职责 |
|------|------|------|
| `core` | `common-thread-pool-core` | 领域模型：`ThreadPoolConfig`、`ThreadPoolStats`、枚举（QueueType/RejectPolicyType） |
| `client-sdk` | `common-thread-pool-client` | 客户端注解、AOP 切面、线程池管理器、统计上报器 |
| `admin-server` | `common-thread-pool-admin-server` | 管理服务端：REST 控制器、MyBatis-Plus DAO、可切换存储后端、API 密钥认证、长轮询订阅 |
| `thread-pool-spring-boot-starter` | `thread-pool-spring-boot-starter` | Spring Boot Starter：将 client-sdk + core 包装为自动配置的依赖 |

**依赖链：** starter → client-sdk → core（admin-server 依赖 core）

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

### 2. 配置运行模式

```yaml
thread:
  pool:
    enabled: true
    mode: LOCAL          # LOCAL | FILE | CS | NACOS
    default-pool:
      core-pool-size: 10
      maximum-pool-size: 20
      queue-capacity: 1000
      keep-alive-time: 60
      queue-type: BLOCKING_QUEUE
      reject-policy-type: ABORT
    pools:
      - name: order-pool
        core-pool-size: 20
        maximum-pool-size: 50
        queue-capacity: 2000
      - name: notification-pool
        core-pool-size: 5
        maximum-pool-size: 10
        queue-capacity: 500
```

### 3. 开启注解支持

```java
@SpringBootApplication
@EnableThreadPool
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

### 4. 使用方式

#### 声明式注解（推荐）

```java
@Service
public class OrderService {

    /** 使用默认线程池异步执行 */
    @AsyncThreadPool
    public CompletableFuture<String> processOrder(String orderId) { ... }

    /** 使用指定线程池 */
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

#### 编程式

```java
ThreadPoolManager manager = ThreadPoolManager.getInstance();
DynamicThreadPoolWrapper pool = manager.getRequiredPool("order-pool");
pool.submit(() -> { /* 业务逻辑 */ });
```

---

## 运行模式详解

| 模式 | 配置值 | 说明 | 适用场景 |
|------|--------|------|----------|
| LOCAL | `mode: LOCAL` | 使用静态配置，不监听变更 | 配置固定 |
| FILE | `mode: FILE` | 监听本地 JSON 配置文件变更 | 单机应用 |
| CS | `mode: CS` | 客户端-管理服务端架构，支持长轮询订阅 | 集中管理 |
| NACOS | `mode: NACOS` | 监听 Nacos 配置中心 | 微服务架构 |

详见各模式的 YAML 配置示例（见下方章节）。

---

## 管理服务端（Admin Server）

CS 模式需要一个管理服务端，提供线程池配置的集中管理和推送。

### 启动服务端

```yaml
server:
  port: 8080

threadpool:
  admin:
    # 存储类型：local（默认，本地 JSON 文件）或 redis-mysql
    storage:
      type: local
    auth-enabled: true              # 是否开启 API 密钥认证
    history-max-size: 100           # 变更历史最大条数
    api-key-storage-path: ./data/api-keys
    config-storage-path: ./data/configs
    stats-storage-path: ./data/stats
```

### 存储后端

| 存储类型 | 配置 | 后端 | 特点 |
|----------|------|------|------|
| 本地文件 | `storage.type: local` | JSON 文件（`./data/`） | 零依赖，开箱即用 |
| Redis + MySQL | `storage.type: redis-mysql` | Redisson RMap + MyBatis-Plus | 分布式、持久化，需 MySQL + Redis |

### API 密钥管理端点

```bash
POST   /api/api-keys                           # 创建密钥（返回明文）
GET    /api/api-keys                           # 列出所有密钥
GET    /api/api-keys/{appId}                   # 查询密钥信息
PUT    /api/api-keys/{appId}                   # 更新密钥
DELETE /api/api-keys/{appId}                   # 删除密钥
POST   /api/api-keys/{appId}/regenerate        # 重新生成
GET    /api/api-keys/{appId}/history?limit=10  # 变更历史
```

### 配置管理端点

```bash
GET    /api/thread-pool/configs/{appId}                  # 获取全量配置
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
POST /open/api/thread-pool/config/{appId}/add                  # 添加单条
POST /open/api/thread-pool/configs/{appId}/add                 # 批量添加
GET  /open/api/thread-pool/config/{appId}/pull?version=0       # 短轮询拉取
GET  /open/api/thread-pool/configs/{appId}/subscribe?version=0 # 长轮询订阅
POST /open/api/thread-pool/stats/report                        # 统计上报
```

> **长轮询订阅**：客户端调用 `subscribe` 接口，服务端通过 `DeferredResult` 挂起请求，配置变更时即时推送（超时返回 304 Not Modified）。

### 客户端配置（CS 模式）

```yaml
thread:
  pool:
    mode: CS
    cs:
      client:
        server-url: http://localhost:8080
        app-id: my-app
        api-key: your-api-key
        enable-long-polling: true
        long-polling-timeout-ms: 30000
        short-polling-interval-ms: 5000
```

---

## 统计监控

### 客户端上报（推送模式）

客户端定时向管理服务端上报线程池运行状态：

```yaml
thread:
  pool:
    cs:
      client:
        stats-report-interval-ms: 10000   # 上报间隔（毫秒）
```

### 编程式获取

```java
ThreadPoolStats stats = ThreadPoolManager.getInstance().getPoolStats("order-pool");
stats.getCorePoolSize();       // 核心线程数
stats.getActiveCount();        // 活跃线程数
stats.getQueueSize();          // 队列大小
stats.getLoadFactor();         // 负载率 = activeCount / maximumPoolSize
stats.getQueueUsageRate();     // 队列使用率 = queueSize / queueCapacity
```

---

## 测试

```bash
mvn test                                # 运行 admin-server 测试（JUnit 5 + Mockito + AssertJ）
```

测试覆盖 31 个类，涵盖控制器、服务、存储后端和工具类。测试配置使用本地文件模式，数据写入临时目录。

---

## 注意事项

1. **模式互斥**：LOCAL / FILE / CS / NACOS 四种模式只能选一种
2. **默认线程池**：`default-pool` 不能被删除
3. **MapStruct**：admin-server 转换器需要 IDE 开启注解处理，或通过 `mvn compile` 生成实现
4. **队列容量修改**需要重新创建线程池
5. **长轮询超时**建议设置 30-60 秒
