# 线程池组件使用指南

## 概述

`common-thread-pool` 是一个功能强大的动态线程池管理组件，支持多种配置模式和动态更新能力。

### 核心特性

- **多种运行模式**：本地静态、文件动态、CS 动态、Nacos 动态
- **动态参数调整**：支持运行时动态修改线程池参数
- **声明式创建**：通过注解方式声明和创建线程池
- **批量配置管理**：支持一次性配置多个线程池
- **长轮询订阅**：高效的配置变更通知机制

### 运行模式

| 模式 | 说明 | 适用场景 |
|------|------|----------|
| LOCAL | 本地静态模式，使用配置文件中的配置 | 配置固定，无需动态变更 |
| FILE | 文件动态模式，监听配置文件变更 | 单机应用，配置文件本地 |
| CS | CS 动态模式，客户端 - 服务端架构 | 集中式配置管理，多应用共享 |
| NACOS | Nacos 动态模式，监听配置中心 | 微服务架构，已有 Nacos 环境 |

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
    # 选择运行模式：LOCAL, FILE, CS, NACOS
    mode: LOCAL

    # 默认线程池配置
    default-pool:
      core-pool-size: 10
      maximum-pool-size: 20
      queue-capacity: 1000
      keep-alive-time: 60
      queue-type: BLOCKING_QUEUE
      reject-policy-type: ABORT

    # 自定义线程池配置列表
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

### 3. 使用方式

#### 方式一：声明式注解（推荐）

```java
@Service
public class OrderService {

    /**
     * 使用默认线程池异步执行
     */
    @AsyncThreadPool
    public CompletableFuture<String> processOrder(String orderId) {
        // 业务逻辑
        return CompletableFuture.completedFuture("Success");
    }

    /**
     * 使用指定线程池异步执行
     */
    @AsyncThreadPool(poolName = "order-pool")
    public CompletableFuture<String> processOrderAsync(String orderId) {
        // 业务逻辑
        return CompletableFuture.completedFuture("Success");
    }

    /**
     * 声明式创建并使用线程池
     */
    @CreateThreadPool(
        poolName = "report-pool",
        corePoolSize = 5,
        maximumPoolSize = 10,
        queueCapacity = 100
    )
    public CompletableFuture<String> generateReport(String reportId) {
        // 业务逻辑 - 自动创建 report-pool 线程池并执行
        return CompletableFuture.completedFuture("Report generated");
    }
}
```

#### 方式二：编程式

```java
@Service
public class NotificationService {

    private final DynamicThreadPoolWrapper notificationPool;

    public NotificationService() {
        ThreadPoolManager manager = ThreadPoolManager.getInstance();
        notificationPool = manager.getRequiredPool("notification-pool");
    }

    public void sendNotification(String message) {
        notificationPool.submit(() -> {
            // 发送通知逻辑
            System.out.println("Sending: " + message);
        });
    }
}
```

## 模式详解

### LOCAL 模式（本地静态）

默认模式，使用配置文件中的配置初始化线程池，不监听变更。

```yaml
thread:
  pool:
    mode: LOCAL
    default-pool:
      core-pool-size: 10
      maximum-pool-size: 20
    pools:
      - name: order-pool
        core-pool-size: 20
        maximum-pool-size: 50
```

### FILE 模式（文件动态）

监听配置文件变更，自动更新线程池。

```yaml
thread:
  pool:
    mode: FILE
    file-watch:
      config-path: config/thread-pool-config.json
      watch-interval-ms: 3000
```

配置文件格式（JSON 数组）：

```json
[
  {
    "poolName": "order-pool",
    "corePoolSize": 20,
    "maximumPoolSize": 50,
    "queueCapacity": 2000,
    "queueType": "BLOCKING_QUEUE",
    "rejectPolicyType": "ABORT"
  },
  {
    "poolName": "notification-pool",
    "corePoolSize": 5,
    "maximumPoolSize": 10,
    "queueCapacity": 500
  }
]
```

### CS 模式（客户端 - 服务端）

#### 服务端配置

```yaml
thread:
  pool:
    mode: CS
    cs:
      server:
        enabled: true
        port: 8088
        config-path: config/thread-pool-config.json
```

服务端 API：
- `GET /api/thread-pool/configs/{appId}` - 获取所有配置
- `POST /api/thread-pool/configs/{appId}` - 保存所有配置
- `GET /api/thread-pool/configs/{appId}/{poolName}` - 获取单个配置
- `POST /api/thread-pool/configs/{appId}/{poolName}` - 保存单个配置
- `DELETE /api/thread-pool/configs/{appId}/{poolName}` - 删除配置
- `GET /api/thread-pool/configs/{appId}/subscribe?version={version}` - 长轮询订阅

#### 客户端配置

```yaml
thread:
  pool:
    mode: CS
    cs:
      client:
        server-url: http://localhost:8088
        app-id: my-app
        api-key: your-api-key
        enable-long-polling: true
        long-polling-timeout-ms: 30000
        short-polling-interval-ms: 5000
```

### NACOS 模式

监听 Nacos 配置中心，动态更新线程池。

```yaml
thread:
  pool:
    mode: NACOS
    nacos:
      server-addr: localhost:8848
      namespace: your-namespace
      data-id: thread-pool-config
      group: DEFAULT_GROUP
      config-type: json
      username: nacos
      password: nacos
      auto-refresh: true
```

Nacos 配置内容（JSON 数组）：

```json
[
  {
    "poolName": "order-pool",
    "corePoolSize": 20,
    "maximumPoolSize": 50,
    "queueCapacity": 2000
  }
]
```

## 动态更新

### 通过 API 更新

```java
ThreadPoolManager manager = ThreadPoolManager.getInstance();

// 更新核心线程数
manager.updateCorePoolSize("order-pool", 30);

// 更新最大线程数
manager.updateMaximumPoolSize("order-pool", 60);

// 更新完整配置
ThreadPoolConfig newConfig = ThreadPoolConfig.builder()
    .poolName("order-pool")
    .corePoolSize(25)
    .maximumPoolSize(55)
    .queueCapacity(3000)
    .build();
manager.updatePool("order-pool", newConfig);
```

### 通过配置文件更新

修改配置文件后，系统会自动检测并更新（FILE 模式）。

### 通过配置中心更新

在 Nacos 控制台修改配置后，系统会自动更新（NACOS 模式）。

## 配置参数说明

### 线程池参数

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| poolName | String | default-pool | 线程池名称 |
| corePoolSize | int | CPU 核心数 | 核心线程数 |
| maximumPoolSize | int | CPU*2 | 最大线程数 |
| keepAliveTime | long | 60 | 空闲线程存活时间 |
| timeUnit | TimeUnit | SECONDS | 时间单位 |
| queueType | String | BLOCKING_QUEUE | 队列类型 |
| queueCapacity | int | 1024 | 队列容量 |
| rejectPolicyType | String | ABORT | 拒绝策略 |
| allowCoreThreadTimeout | boolean | false | 是否允许核心线程超时 |
| threadNamePrefix | String | poolName | 线程名称前缀 |
| daemon | boolean | false | 是否为守护线程 |

### 队列类型

- `ARRAY_BLOCKING_QUEUE` - 有界数组阻塞队列
- `BLOCKING_QUEUE` - 阻塞队列（默认）
- `PRIORITY_BLOCKING_QUEUE` - 优先级阻塞队列
- `SYNCHRONOUS_QUEUE` - 同步移交队列
- `LINKED_BLOCKING_QUEUE` - 链表阻塞队列

### 拒绝策略

- `ABORT` - 抛出异常（默认）
- `DISCARD` - 直接丢弃
- `DISCARD_OLDEST` - 丢弃最老的任务
- `CALLER_RUNS` - 由调用线程处理
- `BLOCKED` - 阻塞等待

## 监控与统计

### 获取线程池统计

```java
ThreadPoolManager manager = ThreadPoolManager.getInstance();
ThreadPoolStats stats = manager.getPoolStats("order-pool");

System.out.println("核心线程数：" + stats.getCorePoolSize());
System.out.println("最大线程数：" + stats.getMaximumPoolSize());
System.out.println("当前线程数：" + stats.getPoolSize());
System.out.println("活跃线程数：" + stats.getActiveCount());
System.out.println("队列大小：" + stats.getQueueSize());
System.out.println("队列容量：" + stats.getQueueCapacity());
System.out.println("已完成任务数：" + stats.getCompletedTaskCount());
System.out.println("负载率：" + String.format("%.2f", stats.getLoadFactor()));
System.out.println("队列使用率：" + String.format("%.2f", stats.getQueueUsageRate()));
```

## 最佳实践

### 1. 线程池命名

使用有意义的名称，便于监控和问题排查：

```yaml
pools:
  - name: order-process-pool    # 订单处理
  - name: payment-notify-pool   # 支付通知
  - name: report-generate-pool  # 报表生成
```

### 2. 线程数设置

- **CPU 密集型**：CPU 核心数 + 1
- **IO 密集型**：CPU 核心数 * 2 或更多
- **混合型**：根据实际负载调整

### 3. 拒绝策略选择

- **重要业务**：`BLOCKED` 或 `CALLER_RUNS`
- **可丢弃业务**：`DISCARD`
- **实时性要求高**：`ABORT`

### 4. 监控告警

建议监控以下指标：
- 负载率 > 80% 持续 5 分钟
- 队列使用率 > 80%
- 拒绝任务数量

## 注意事项

1. **模式互斥**：四种模式互斥，只能选择一种
2. **默认线程池**：`default-pool` 不能被删除
3. **队列容量**：修改队列容量需要重新创建线程池
4. **长轮询超时**：建议设置合理的超时时间（30-60 秒）
