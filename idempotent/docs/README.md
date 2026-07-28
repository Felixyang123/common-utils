# idempotent 幂等组件

通用幂等中间件，提供 `@Idempotent` 注解 + AOP 切面，支持分布式多实例部署。

## 快速开始

### 1. 引入依赖

```xml
<dependency>
    <groupId>com.lezai</groupId>
    <artifactId>idempotent</artifactId>
    <version>${project.version}</version>
</dependency>

<!-- Redis 存储/锁（默认，必须引入） -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

组件通过 Spring Boot 3 标准自动装配（`AutoConfiguration.imports`），jar 在 classpath 即自动生效，无需任何注解。

### 2. 配置

```yaml
idempotent:
  enabled: true                    # 默认 true，可关闭
  storage: redis                   # 默认 redis（可选 local / jdbc）
  lock: redis                      # 默认 redis（可选 local）
  expire-time: 3600                # 幂等记录有效期（秒），默认 3600
  max-fail-retry-count: 3          # FAILED 记录最大重试次数，默认 3
  redis:
    key-prefix: "idempotent:"      # Redis 键前缀
  jdbc:
    table-name: idempotent_record  # JDBC 表名
    auto-create-table: true        # 自动建表
  cleanup:
    enabled: true                  # JDBC 定时清理开关
    interval-seconds: 1800         # 清理间隔（秒）
    batch-size: 1000               # 每次清理批次大小
  security:
    result-type-whitelist:         # 反序列化白名单（包前缀），为空时放行但 WARN
      - "com.lezai."
    anonymous-strategy: REJECT     # 匿名用户策略：REJECT / ALLOW
```

### 3. 使用

```java
@Service
public class OrderService {

    @Idempotent(key = "'orderId:' + #orderId")
    public Order createOrder(String orderId, OrderRequest request) {
        // 业务逻辑
        return orderRepository.save(new Order(orderId, request));
    }
}
```

## 注解属性

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `key` | String | `""` | SpEL 表达式，为空时使用 keyGenerator |
| `prefix` | String | `""` | 业务前缀（存储层前缀由 `redis.keyPrefix` 承担） |
| `lockLeaseTime` | long | 60 | 锁租约时长（秒），看门狗自动续期 |
| `failFast` | boolean | true | true=立即抛异常，false=等待并重试 |
| `maxRetryCount` | int | 10 | 重试次数（failFast=false 时有效） |
| `retryInterval` | long | 100 | 重试间隔毫秒（failFast=false 时有效） |
| `returnResultOnDuplicate` | boolean | true | 重复请求返回缓存结果 vs 抛异常 |
| `storeResult` | boolean | true | 是否缓存执行结果 |
| `keyGenerator` | Class | DefaultKeyGenerator | 自定义键生成器（key 为空时使用） |

## 存储策略对比

| 维度 | Local | Redis | JDBC |
|------|-------|-------|------|
| 分布式 | ❌ 单实例 | ✅ 多实例 | ✅ 多实例 |
| 性能 | 最快 | 快 | 较慢 |
| 持久化 | 进程内存 | Redis | 数据库 |
| TTL 语义 | Caffeine 全局 TTL | Redis TTL | record.expireTime |
| 过期清理 | Caffeine 自动 | Redis 自动 | 定时任务（已内置） |
| 适用场景 | 测试/单实例 | 生产推荐 | 需审计/无 Redis |

## 异常与 HTTP 状态码

组件提供 `@RestControllerAdvice` 自动映射（需 spring-web 在 classpath）：

| 异常 | HTTP 状态码 | 场景 |
|------|------------|------|
| `IdempotentException` | 409 Conflict | 重复请求/处理中 |
| `IdempotentLockException` | 429 Too Many Requests | 锁获取失败 |
| `IdempotentStorageException` | 503 Service Unavailable | 存储不可用 |
| `IdempotentExecutionException` | 500 | 业务执行异常 |

## 已知限制

1. **同类自调用**：`this.method()` 绕过 AOP 代理，`@Idempotent` 不生效。需通过注入自身或使用 `AopContext.currentProxy()` 解决。
2. **数据库仅支持 MySQL**（8.0+）：DDL 使用 `ON DUPLICATE KEY UPDATE`、`AUTO_INCREMENT` 等 MySQL 方言。
3. **at-least-once 边界**：崩溃窗口（≈ 锁 leaseTime，默认 60s）内，同 key 可能被重复执行。业务侧需对副作用做幂等（如数据库唯一约束）。
4. **key 非空时跳过默认生成器**：升级后存量 key 格式可能变化（等效缓存全失效一次）。

## 可观测性

- **Micrometer Metrics**（optional）：`idempotent.request.total`、`idempotent.request.duplicate`、`idempotent.request.takeover`、`idempotent.execute.duration`。
- **结构化日志**：`key=xxx method=xxx status=xxx duration=xxxms`。
- **requestId**：从 MDC `traceId` 自动写入幂等记录。

## 参考

- [ADR-0001: LocalLockProvider 原子化设计](../../docs/adr/0001-local-lock-entry-compute-atomicity.md)
- [ADR-0002: IdempotentStorage 接口契约](../../docs/adr/0002-idempotent-storage-explicit-semantics-contract.md)
- [ADR-0003: 部署形态与默认配置](../../docs/adr/0003-deployment-form-default-config.md)
- [ADR-0004: 锁即租约崩溃接管](../../docs/adr/0004-lock-as-lease-crash-recovery.md)
- [ADR-0005: FAILED 冷却与 DDL 版本迁移](../../docs/adr/0005-fail-cooldown-ddl-migration.md)
