# idempotent 组件迁移指南

本文档覆盖本次企业级修缮引入的 **Breaking 变更**，帮助现有接入方平滑升级。

---

## 1. `@EnableIdempotent` 注解已删除

**影响**：启动类上的 `@EnableIdempotent` 注解不再存在，编译报错。

**迁移**：删除启动类上的 `@EnableIdempotent` 注解。组件通过 `AutoConfiguration.imports` 自动装配，jar 在 classpath 即生效。

```java
// 旧
@SpringBootApplication
@EnableIdempotent
public class Application { ... }

// 新
@SpringBootApplication
public class Application { ... }
```

---

## 2. 默认存储/锁策略从 LOCAL 改为 REDIS

**影响**：未显式配置 `idempotent.storage`/`idempotent.lock` 的服务，升级后默认使用 Redis。如果 classpath 没有 `spring-boot-starter-data-redis`，启动时会回退到 Local 并输出 WARN 日志。

**迁移**：
- 多实例部署：确保引入 Redis 依赖，无需额外配置。
- 单实例/测试：显式配置 `idempotent.storage=local` + `idempotent.lock=local`。

```yaml
# 单实例/测试环境显式降级
idempotent:
  storage: local
  lock: local
```

---

## 3. `tryLockTime` 重命名为 `lockLeaseTime`，默认值 3600 → 60

**影响**：使用 `@Idempotent(tryLockTime=...)` 的代码编译报错。

**迁移**：改为 `lockLeaseTime`，默认 60s 由看门狗自动续期，一般无需手动设置。

```java
// 旧
@Idempotent(tryLockTime = 120)

// 新
@Idempotent(lockLeaseTime = 120)  // 通常不需要设置，默认 60s 足够
```

---

## 4. `prefix` 默认值从 `"idempotent:"` 改为 `""`

**影响**：使用默认 prefix 的 `@Idempotent` 方法，升级后生成的幂等键不再包含 `idempotent:` 前缀。存储层前缀由 `idempotent.redis.keyPrefix`（默认 `"idempotent:"`）承担。

**迁移**：
- 如果你的 Redis 里已有旧格式 key（`idempotent:idempotent:xxx`），升级后新请求会用新 key（`idempotent:xxx`），旧 key 等 TTL 自然过期。
- 如果业务依赖 prefix 拼接逻辑，显式指定 `prefix`。

---

## 5. keyGenerator 语义修正：key 非空时不再追加

**影响**：`@Idempotent(key = "#orderId")` 之前会追加默认生成器输出，升级后只用 SpEL 结果。

**迁移**：这是行为修正，不需要改代码。但升级后**新旧 key 格式不同**，等效于缓存全失效一次。在业务低峰期升级。

---

## 6. Caffeine 依赖变为 optional

**影响**：使用 `idempotent.storage=local` 的服务需要显式引入 Caffeine。

**迁移**：

```xml
<dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
</dependency>
```

---

## 7. common-lock 依赖变为 optional

**影响**：使用 `idempotent.lock=redis` 的服务需要显式引入 common-lock。

**迁移**：

```xml
<dependency>
    <groupId>com.lezai</groupId>
    <artifactId>common-lock</artifactId>
    <version>${project.version}</version>
</dependency>
```

---

## 8. JDBC 表需新增列

**影响**：已有的 JDBC 幂等表缺少 `fail_count` 和 `request_id` 列。

**迁移**：组件启动时自动检测并 ALTER TABLE 补列（DDL 版本迁移），**无需手动执行 SQL**。如果自动迁移失败，手动执行：

```sql
ALTER TABLE idempotent_record ADD COLUMN fail_count INT NOT NULL DEFAULT 0 COMMENT '失败次数';
ALTER TABLE idempotent_record ADD COLUMN request_id VARCHAR(64) COMMENT '请求ID（用于追踪）';
```

---

## 9. 配置属性新增/约束

**影响**：`idempotent.expire-time`、`idempotent.local.max-size` 等属性加了 `@Min`/`@NotBlank`/`@Pattern` 校验。配非法值（如 `expire-time: 0`、`table-name: ""`）会在启动时报错。

**迁移**：检查现有配置，确保值合法。

---

## 升级清单

- [ ] 删除 `@EnableIdempotent`
- [ ] 确认 Redis 依赖已引入（多实例）或显式配 `storage=local`（单实例）
- [ ] `tryLockTime` → `lockLeaseTime`
- [ ] 评估 prefix/keyGenerator 变更对存量 key 的影响（低峰期升级）
- [ ] 如用 Local 存储，加 Caffeine 依赖
- [ ] 如用 Redis 锁，加 common-lock 依赖
- [ ] 检查配置属性合法性
