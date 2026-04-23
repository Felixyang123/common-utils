# 线程池组件新架构设计文档

## 架构概述

本次重构基于**策略模式**和**模板模式**设计，将 CS 模式和配置文件模式解耦，提供统一的配置源抽象接口。

## 核心组件

### 1. 策略接口层

#### ConfigSourceStrategy (配置源策略接口)
```java
public interface ConfigSourceStrategy {
    ThreadPoolConfig getConfig(String appId, String poolName);
    List<ThreadPoolConfig> getAllConfigs(String appId);
    void saveConfig(String appId, ThreadPoolConfig config);
    void saveAllConfigs(String appId, List<ThreadPoolConfig> configs);
    long getVersion(String appId);
    void registerListener(String appId, ConfigChangeListener listener);
    void deleteConfig(String appId, String poolName);
}
```

#### AbstractConfigSourceStrategy (抽象基类)
- 实现模板方法 `applyConfigs()`，统一处理配置的创建/更新逻辑
- 定义抽象钩子方法供子类实现

### 2. 策略实现层

#### LocalConfigSourceStrategy (本地文件策略)
- 从本地文件读取配置
- 支持文件监听变更通知
- 适用于 LOCAL 和 FILE 模式

#### RemoteConfigSourceStrategy (远程配置策略)
- 通过 HTTP 客户端从服务端拉取配置
- 支持长轮询订阅配置变更
- 适用于 CS 模式

#### NacosConfigListener (Nacos 配置策略)
- 监听 Nacos 配置中心
- 支持 JSON/YAML/Properties 格式
- 适用于 NACOS 模式

### 3. 服务层

#### ThreadPoolService (线程池服务)
封装线程池的核心操作，支持 CS 模式扩展点：

- **getPool()**: 获取线程池
  - 本地不存在时，CS 模式下从服务端获取默认配置
  - 服务端不存在时保存客户端默认参数并返回

- **registerPool()**: 注册线程池
  - 原子操作，不存在则创建
  - CS 模式下同步配置到服务端

- **updatePool()**: 更新线程池
  - 原子操作，存在则更新，不存在则创建
  - CS 模式下同步配置到服务端

- **deletePool()**: 删除线程池
  - CS 模式下同步删除服务端配置

### 4. 初始化层

#### ThreadPoolInitializer (线程池初始化器)
按顺序执行三个阶段的加载：

1. **阶段 1**: 加载声明式注解（由 AOP 切面在运行时处理）
2. **阶段 2**: 加载配置文件/配置源，执行 upsert 操作
3. **阶段 3**: （已合并到阶段 2）查询服务端配置并应用

## 模式对比

| 特性 | LOCAL | FILE | CS | NACOS |
|------|-------|------|----|----|
| 配置源 | 本地文件 | 本地文件 | 远程服务端 | Nacos |
| 动态更新 | ❌ | ✅ | ✅ | ✅ |
| 文件监听 | ❌ | ✅ | - | - |
| 长轮询 | - | - | ✅ | ✅ |
| 集中管理 | - | - | ✅ | ✅ |
| 适用场景 | 简单应用 | 单机应用 | 多应用 | 微服务 |

## 配置示例

### LOCAL 模式（本地静态）
```yaml
thread:
  pool:
    enabled: true
    mode: LOCAL
    default-pool:
      core-pool-size: 10
      maximum-pool-size: 20
      queue-capacity: 1024
    pools:
      - name: order-pool
        core-pool-size: 5
        maximum-pool-size: 10
```

### FILE 模式（文件动态）
```yaml
thread:
  pool:
    enabled: true
    mode: FILE
    file-watch:
      config-path: config/thread-pool.yaml
      watch-interval-ms: 3000
```

### CS 模式（客户端 - 服务端）
```yaml
# 客户端配置
thread:
  pool:
    enabled: true
    mode: CS
    cs:
      client:
        server-url: http://localhost:8088
        app-id: my-app
        api-key: your-api-key
        enable-long-polling: true
        long-polling-timeout-ms: 30000

# 服务端配置（可选）
# thread.pool.cs.server.enabled: true
# thread.pool.cs.server.port: 8088
```

### NACOS 模式
```yaml
thread:
  pool:
    enabled: true
    mode: NACOS
    nacos:
      server-addr: localhost:8848
      namespace: your-namespace
      data-id: thread-pool-config
      group: DEFAULT_GROUP
      config-type: yaml
      auto-refresh: true
```

## 使用方式

### 1. 编程式使用

```java
@Autowired
private ThreadPoolService threadPoolService;

// 获取线程池（不存在则自动创建）
DynamicThreadPoolWrapper pool = threadPoolService.getPool("my-pool");

// 使用自定义配置创建
ThreadPoolConfig config = ThreadPoolConfig.builder()
    .poolName("custom-pool")
    .corePoolSize(10)
    .maximumPoolSize(20)
    .build();
DynamicThreadPoolWrapper customPool = threadPoolService.getPool("custom-pool", config);

// 注册线程池（CS 模式下会同步到服务端）
threadPoolService.registerPool(config);

// 更新线程池
threadPoolService.updatePool(config);

// 删除线程池
threadPoolService.deletePool("my-pool");
```

### 2. 声明式使用

```java
// 使用 @CreateThreadPool 注解声明线程池
@CreateThreadPool(
    poolName = "report-pool",
    corePoolSize = 5,
    maximumPoolSize = 10,
    queueCapacity = 100
)
public CompletableFuture<String> generateReport(String reportId) {
    // 方法自动在 report-pool 线程池中执行
    return CompletableFuture.completedFuture("report");
}

// 使用 @AsyncThreadPool 注解
@AsyncThreadPool("order-pool")
public void processOrder(Order order) {
    // 方法自动在 order-pool 线程池中执行
}
```

## 核心流程

### 获取线程池流程
```
1. 查询本地 ThreadPoolManager
   ├── 存在 → 返回
   └── 不存在 → 创建
        ├── CS 模式 → 从服务端获取默认配置
        │    ├── 服务端有配置 → 使用服务端配置创建
        │    └── 服务端无配置 → 使用本地默认配置创建并保存到服务端
        └── 非 CS 模式 → 使用本地默认配置创建
```

### 注册线程池流程
```
1. 原子创建线程池（putIfAbsent）
2. CS 模式 → 同步配置到服务端
```

### 更新线程池流程
```
1. 检查是否存在
   ├── 存在 → 更新配置
   └── 不存在 → 创建线程池
2. CS 模式 → 同步配置到服务端
```

### 初始化流程
```
1. Phase 1: 声明式注解加载（AOP 运行时处理）
2. Phase 2: 从配置源加载配置并应用
   ├── 从 ConfigSourceStrategy 获取所有配置
   ├── 遍历配置执行 upsert 操作
   └── 无配置时使用默认配置降级
```

## 扩展性

### 添加新的配置源

1. 创建新的策略类继承 `AbstractConfigSourceStrategy`
2. 实现抽象钩子方法：
   - `doGetConfig()`
   - `doGetAllConfigs()`
   - `doSaveConfig()`
   - `doSaveAllConfigs()`
   - `doGetVersion()`
   - `doRegisterListener()`

3. 在 `ThreadPoolAutoConfiguration.configSourceStrategy()` 中添加新模式的 case

### 示例：添加 Apollo 配置源

```java
@Slf4j
public class ApolloConfigSourceStrategy extends AbstractConfigSourceStrategy {

    private final String appId;
    private final Config config;

    public ApolloConfigSourceStrategy(String namespace) {
        super("APOLLO");
        this.appId = ConfigService.getAppId();
        this.config = ConfigService.getConfig(namespace);
    }

    @Override
    protected ThreadPoolConfig doGetConfig(String appId, String poolName) {
        // 从 Apollo 读取配置
    }

    // ... 实现其他钩子方法
}
```

## 依赖说明

### 核心依赖
- Spring Boot 2.x/3.x
- Lombok
- FastJSON2
- OkHttp3 (CS 模式需要)

### 可选依赖
- Nacos Client (NACOS 模式需要)
- Apollo Client (扩展 Apollo 支持时需要)

## 迁移指南

### 从旧版本迁移

1. **移除旧的 ConfigClient 使用**
   - 旧版本：直接使用 `ConfigClient` 拉取配置
   - 新版本：通过 `ThreadPoolService` 统一操作

2. **移除旧的文件存储类**
   - 删除 `LocalFileConfigStorage` 相关引用
   - 使用 `LocalConfigSourceStrategy` 替代

3. **更新配置格式**
   - 确保 `thread.pool.mode` 配置正确
   - 检查 CS 模式配置路径 `thread.pool.cs.client`

## 总结

新架构的优势：
1. **解耦**: CS 模式和配置模式完全解耦
2. **统一**: 统一的配置源抽象接口
3. **扩展**: 易于添加新的配置源
4. **简洁**: 使用模式简化代码逻辑
5. **安全**: 原子操作保证并发安全
