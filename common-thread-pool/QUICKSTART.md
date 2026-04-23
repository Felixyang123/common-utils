# 线程池组件快速开始

## 1. 模块依赖

```
common-thread-pool/           # 核心组件模块
thread-pool-spring-boot-starter/  # Spring Boot Starter 模块
```

## 2. 添加依赖

在项目的 pom.xml 中添加：

```xml
<dependency>
    <groupId>com.lezai</groupId>
    <artifactId>thread-pool-spring-boot-starter</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

## 3. 基础使用

### 方式一：声明式注解（推荐）

**步骤 1：** 在 Spring Boot 启动类上添加注解（可选，会自动配置）

```java
@EnableThreadPool
@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

**步骤 2：** 配置 application.yml

```yaml
thread:
  pool:
    enabled: true
    default-pool:
      core-pool-size: 10
      maximum-pool-size: 20
      queue-capacity: 1000
    pools:
      - name: order-pool
        core-pool-size: 20
        maximum-pool-size: 50
        queue-capacity: 2000
```

**步骤 3：** 使用注解

```java
@Service
public class OrderService {

    @AsyncThreadPool  // 使用默认线程池
    public CompletableFuture<String> processOrder(String orderId) {
        // 业务逻辑
        return CompletableFuture.completedFuture("Success");
    }

    @AsyncThreadPool(poolName = "order-pool")  // 使用指定线程池
    public CompletableFuture<String> processOrderAsync(String orderId) {
        // 业务逻辑
        return CompletableFuture.completedFuture("Success");
    }
}
```

### 方式二：编程式

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

## 4. 高级功能

### CS 模式（集中配置管理）

**服务端配置：**

```yaml
thread:
  pool:
    server:
      enabled: true
      port: 8088
      config-path: config/thread-pool
```

**客户端配置：**

```yaml
thread:
  pool:
    client-enabled: true
    client:
      server-url: http://localhost:8088
      app-id: my-app
      api-key: your-api-key
      pull-interval-ms: 5000
```

### 配置文件监听模式

```yaml
thread:
  pool:
    file-watch-enabled: true
    file-watch:
      config-path: config/thread-pool.yaml
      watch-interval-ms: 3000
```

### 动态更新线程池

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
    .build();
manager.updatePool("order-pool", newConfig);
```

### 查询线程池状态

```java
ThreadPoolManager manager = ThreadPoolManager.getInstance();
ThreadPoolStats stats = manager.getPoolStats("order-pool");

System.out.println("核心线程数：" + stats.getCorePoolSize());
System.out.println("活跃线程数：" + stats.getActiveCount());
System.out.println("队列大小：" + stats.getQueueSize());
System.out.println("已完成任务数：" + stats.getCompletedTaskCount());
System.out.println("负载率：" + String.format("%.2f", stats.getLoadFactor()));
```

## 5. 配置参数说明

| 参数 | 默认值 | 说明 |
|------|--------|------|
| core-pool-size | CPU 核心数 | 核心线程数 |
| maximum-pool-size | CPU*2 | 最大线程数 |
| queue-capacity | 1024 | 队列容量 |
| keep-alive-time | 60 | 空闲线程存活时间（秒） |
| queue-type | BLOCKING_QUEUE | 队列类型 |
| reject-policy-type | ABORT | 拒绝策略 |

## 6. 测试验证

运行测试用例：

```bash
# 核心功能测试
mvn test -Dtest=ThreadPoolConfigTest
mvn test -Dtest=DynamicThreadPoolWrapperTest
mvn test -Dtest=ThreadPoolManagerTest
mvn test -Dtest=ThreadPoolConcurrencyTest
```

## 7. 参考资料

- [详细使用文档](README.md)
- [实现文档](IMPLEMENTATION.md)
- [示例代码](src/test/java/com/lezai/threadpool/examples/)
