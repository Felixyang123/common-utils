# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**common-utils** 是一个基于 Java 21 + Spring Boot 3.5.3 的多模块工具库，提供幂等、防重复提交、分布式锁、限流、缓存、任务流等通用能力。各模块均为独立的 Spring Boot Starter，通过 `@EnableXxx` 注解启用。

## Build & Test Commands

```bash
# 构建整个项目
./mvnw clean install

# 构建单个模块（以 idempotent 为例，需先安装依赖模块）
./mvnw -pl idempotent clean install

# 运行全量测试
./mvnw test

# 运行单个模块测试
./mvnw -pl idempotent test

# 运行单个测试类
./mvnw -pl idempotent test -Dtest=IdempotentExecutionManagerTest

# 运行单个测试方法
./mvnw -pl idempotent test -Dtest=IdempotentExecutionManagerTest#testFirstRequestSuccess
```

> 使用 `mvnw.cmd` 替代 `mvnw`（Windows 环境）。

## Module: idempotent（幂等性控制）

### 快速启用

1. 启动类标注 `@EnableIdempotent`
2. 需要幂等控制的方法标注 `@Idempotent`
3. 通过 `application.yml` 配置策略（前缀 `idempotent`）

### 核心架构

```
@EnableIdempotent
  └→ IdempotentAutoConfiguration（自动配置，@EnableLock 启用 common-lock）
       ├→ IdempotentAspect          ← AOP 切面，拦截 @Idempotent 方法
       └→ IdempotentExecutionManager ← 核心协调器
            ├→ IdempotentKeyResolver  ← 幂等键解析（SpEL / 自定义生成器）
            ├→ IdempotentStorage      ← 幂等记录存储（策略模式）
            └→ IdempotentLockProvider ← 并发锁（策略模式）
```

### 执行流程

1. **解析幂等键** — `IdempotentKeyResolver`：优先 SpEL 表达式（`#param`），否则走默认生成器（`类名.方法名.参数hash`），最终键 = `prefix + parsedKey + generatorKey`
2. **查询记录** — 从 Storage 获取 `IdempotentRecord`
3. **记录已存在** — 按状态分发：
   - `PROCESSING`：`failFast=true` 直接抛异常；否则等待重试（`maxRetryCount`/`retryInterval`）
   - `FAILED`：重新获取锁并执行业务
   - `SUCCEEDED`：`returnResultOnDuplicate=true` 返回缓存结果（fastjson2 反序列化）；否则抛异常
4. **记录不存在** — 获取锁 → 双重检查 → 保存 `PROCESSING` → 执行业务 → 保存 `SUCCEEDED/FAILED` → 释放锁

### 策略扩展点

| 扩展点 | 接口 | 实现 | 切换方式 |
|--------|------|------|----------|
| 存储 | `IdempotentStorage` | `LocalIdempotentStorage`（Caffeine，默认）/ `RedisIdempotentStorage` / `JdbcIdempotentStorage` | `idempotent.storage=LOCAL\|REDIS\|JDBC` |
| 锁 | `IdempotentLockProvider` | `LocalLockProvider`（Caffeine+ReentrantLock，默认）/ `RedisLockProvider`（依赖 common-lock） | `idempotent.lock=LOCAL\|REDIS` |
| 键生成 | `IdempotentKeyGenerator` | `DefaultKeyGenerator` / `SessionIdKeyGenerator` / `UserIdKeyGenerator` | `@Idempotent(keyGenerator=...)` |

### 关键设计约束

- **锁与存储解耦**：锁策略和存储策略独立配置，可任意组合（如 LOCAL 锁 + JDBC 存储）
- **JDBC 存储**：使用 MySQL `ON DUPLICATE KEY UPDATE` 实现幂等记录的 upsert；表结构定义在 `sql/ddl.sql`，启动时由 `IdempotentTableInitializer` 自动建表
- **结果缓存**：`storeResult=true` 时通过 fastjson2 序列化返回值，重复请求直接反序列化返回，不执行业务
- **依赖模块**：Redis 锁策略依赖 `common-lock` 模块的 `RedisDistributeLock`；`@EnableLock` 由自动配置隐式启用
- **Web 依赖可选**：`spring-boot-starter-web`、`spring-boot-starter-data-redis`、`spring-boot-starter-jdbc` 均为 optional，按需引入

### 配置属性（前缀 `idempotent`）

- `enabled` (Boolean, default true) — 总开关
- `storage` (StorageStrategy, default LOCAL) — 存储策略
- `lock` (LockStrategy, default LOCAL) — 锁策略
- `expire-time` (Long, default 3600) — 默认过期时间（秒）
- `local.max-size` (Integer, default 10000) — 本地存储最大条目
- `redis.key-prefix` (String, default "idempotent:") — Redis 键前缀
- `jdbc.table-name` (String, default "idempotent_record") — 数据库表名
- `jdbc.auto-create-table` (Boolean, default true) — 是否自动建表

### 异常体系

- `IdempotentException` — 基类（重复请求、重试耗尽）
- `IdempotentExecutionException` — 业务执行失败（包装原始异常）
- `IdempotentLockException` — 获取锁失败

### 测试规范

- 测试框架：JUnit 5 + Mockito（`@ExtendWith(MockitoExtension.class)`）
- 核心测试：`IdempotentExecutionManagerTest`（覆盖首次执行、重复请求、并发双重检查、锁释放、失败重试等场景）
- 存储/锁策略测试：各实现类有独立单元测试
