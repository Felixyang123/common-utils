# ADR-0005: FAILED 冷却与 DDL 版本迁移

## 状态

accepted (2026-07-26)

## 背景与决策

幂等记录进入 FAILED 状态后，每个后续请求都会触发业务重执行——如果失败原因是永久性的（下游服务不可用、参数校验恒失败），每次请求都打到下游，造成重试放大。同时，JDBC 存储的 DDL 管理仅有 `CREATE TABLE IF NOT EXISTS`，新增字段（如 `fail_count`、`request_id`）时旧表无法自动升级。

我们决定：
1. **FAILED 冷却**：记录 `failCount`，超过 `maxFailRetryCount`（默认 3）后不再重执行，直接返回上次错误。key 等 TTL 过期或运维手动清除后恢复。
2. **DDL 版本迁移**：启动时查询 `INFORMATION_SCHEMA.COLUMNS`，对比期望列集，缺列自动 `ALTER TABLE ADD COLUMN`。

## 关键理由

| 维度 | 分析 |
|------|------|
| 为什么冷却而非无限重试 | 永久性失败的无限重试 = 线性放大对下游的请求量。冷却后 fail-fast，保护下游 + 快速反馈调用方 |
| 为什么 failCount 存记录而非内存 | 多实例共享同一幂等记录，内存计数不跨实例；记录级 failCount 保证全局一致的冷却语义 |
| 为什么 DDL 用 INFORMATION_SCHEMA 而非 Flyway | Flyway 增加外部依赖和迁移文件管理成本；INFORMATION_SCHEMA 查询 + ALTER TABLE 对幂等表（小表、低频变更）足够轻量 |
| 为什么 cleanExpiredRecords 分批 | 单条 `DELETE FROM ... WHERE expire_time < NOW()` 可能删除数十万行，触发大事务锁表 + 主从延迟。分批 LIMIT 循环删除避免此风险 |

## 决策影响

- **IdempotentRecord 新增** `failCount` 字段（Integer）
- **IdempotentProperties 新增** `maxFailRetryCount`（默认 3）+ `cleanup` 配置块（enabled/intervalSeconds/batchSize）
- **IdempotentExecutionManager.handleFailRecord**：冷却判断 `failCount >= maxFailRetryCount` → 抛异常不重执行
- **saveFailedRecord**：累加 `failCount`
- **三端存储持久化 failCount**：JDBC 加列 + INSERT/RowMapper；Redis/Local 自动（对象序列化）
- **IdempotentTableInitializer**：新增 `migrateSchema(tableName)` + `columnExists(tableName, columnName)`；`cleanExpiredRecords` 改为分批 LIMIT 循环
- **ddl.sql + buildCreateTableSql**：补齐 `fail_count INT DEFAULT 0`、`request_id VARCHAR(64)`
- **定时清理调度**：IdempotentAutoConfiguration 新增 `IdempotentCleanupScheduler`（`@Scheduled`）

## 权衡与后果

- **正面**：毒 key 自动冷却保护下游；DDL 自动迁移免运维；分批删除避免锁表
- **冷却语义**：冷却后 key 等 TTL 过期才恢复（默认 1h）。如果需要立即恢复，运维可手动 DELETE 该记录。没有提供"一键清除"API（后续可加）
- **DDL 迁移局限**：仅支持加列，不支持改列/删列/改类型。复杂 schema 变更仍需手动
- **分批删除的原子性**：每批独立事务，中途失败不会回滚已删除的批次——对过期清理场景可接受

## 相关文件

- `idempotent/src/main/java/com/lezai/idempotent/core/IdempotentRecord.java`
- `idempotent/src/main/java/com/lezai/idempotent/core/IdempotentExecutionManager.java`
- `idempotent/src/main/java/com/lezai/idempotent/config/IdempotentTableInitializer.java`
- `idempotent/src/main/java/com/lezai/idempotent/config/IdempotentProperties.java`
- `idempotent/src/main/java/com/lezai/idempotent/config/IdempotentAutoConfiguration.java`（CleanupScheduler）
- `idempotent/src/main/resources/sql/ddl.sql`
