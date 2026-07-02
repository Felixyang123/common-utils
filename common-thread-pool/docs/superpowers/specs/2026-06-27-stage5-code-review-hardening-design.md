# 阶段 5 · 代码审查加固 · 设计文档

> 文档类型：**阶段专属 spec**（路线图 `2026-06-22-common-thread-pool-production-hardening-design.md` 的阶段 5 展开）
> 编写日期：2026-06-27
> 模块：`admin-server`（主）、`client-sdk`（少量）、`core`（少量）
> 来源：open-code-review (ocr) 自动审查结果 — `dynamic-tp` 分支 vs `main`，共 89 个 common-thread-pool 问题
> 配套领域文档：`CONTEXT.md`、`docs/adr/0001-cs-mode-availability-first-config.md`
> 后续：本 spec 经用户审阅后，由 writing-plans 展开为实现计划。

---

## 0. 目标与范围

基于 open-code-review 对 `dynamic-tp` 分支的全面审查结果，修复 admin-server 和 client-sdk 中的编译阻塞、SQL 运行时错误、安全漏洞、竞态条件、数据完整性问题和代码质量问题。

审查覆盖 177 个文件，产出 89 个 common-thread-pool 问题（18 High / 65 Medium / 6 Low 已过滤）。

本阶段聚焦 **admin-server 层**（问题最密集），少量涉及 client-sdk 和 core。

---

## 1. 修复簇

### 簇 A：编译/SQL 运行时阻塞（High × 3）

**问题**：
1. `ThreadPoolConfig.java:18-20` — `@Builder` 与手动 `ThreadPoolConfigBuilder` 静态内部类冲突 → **重复类编译错误**，阻塞 core 模块
2. `ThreadPoolConfigMapper.java:13-14` — `Map<String, Long>` 返回类型（无 `@MapKey`）在 GROUP BY 多行时抛 `TooManyResultsException`
3. `ThreadPoolConfigMapper.java:16-17` — `IN (#{appIds})` 不展开 List，绑定为 `toString()` → 无效 SQL

**设计**：
1. 移除 `@Builder`，替换为 `@AllArgsConstructor(access = AccessLevel.PACKAGE)`（保留手动 builder 的动态 `maximumPoolSize` 逻辑）
2. 添加 `@MapKey("app_id")`，返回类型改为 `Map<String, Map<String, Object>>`
3. 使用 `<script>` + `<foreach>` 展开集合参数

### 簇 B：版本管理与缓存一致性（High × 4）

**问题**：
1. `ThreadPoolConfigPersistenceService.java:239` — 添加线程池时 `configApp.setVersion(configApp.getVersion())` 是空操作 → CS 客户端无法感知新增池
2. `ThreadPoolConfigPersistenceService.java:173-177` — 删除不存在的池仍递增版本号 → 虚假变更通知
3. `RedisMysqlConfigStorage.java:220-224` — 合并缓存配置时版本号未传递 → 陈旧版本触发不必要的 DB 重载
4. `RedisMysqlConfigStorage.java:186-243` — `getConfigs()` 和 `compute()` 返回 null 时三处 NPE

**设计**：
1. `addConfigApp` 中改为 `setVersion(getVersion() + 1)`
2. `deleteByAppIdAndPoolName` 在 `configOptional.isEmpty()` 时提前返回，不递增版本
3. 合并缓存时同步 `oldConfigAppDto.setVersion(configAppDto.getVersion())`
4. 所有 `compute()` 结果和 `getConfigs()` 调用前添加 null 检查，null 时抛 `StorageException`

### 簇 C：安全漏洞（High × 3）

**问题**：
1. `ApiKeyController.java:23-27` — `/api/api-keys/**` 端点完全未认证（`ApiKeyAuthInterceptor` 只覆盖 `/open/api/thread-pool/**`）
2. `OpenThreadPoolConfigController.java:60-63` — timeout 参数无 `@Max` → 恶意客户端传 `Long.MAX_VALUE` 可耗尽资源（DoS）
3. `OpenThreadPoolConfigController.java:24-28` — `@Valid` 在 `@RequestParam` 上不生效，需类级 `@Validated`

**设计**：
1. 在 `WebMvcConfig` 中为 `/api/api-keys/**` 注册认证拦截器（或复用 `ApiKeyAuthInterceptor`）
2. 添加 `@Max(value = 60000)` 注解
3. 在 `OpenThreadPoolConfigController` 类上添加 `@Validated`

### 簇 D：竞态条件（High × 3）

**问题**：
1. `ApiKeyAdminService.java:44-47` — `exists()` + `saveApiKey()` 的 check-then-act 非原子 → 并发 upsert 覆盖 hash
2. `LocalFileApiKeyStorage.java:125-133` — `regenerateApiKey` 在 `compute()` 外生成 key → 并发时返回的 key 与存储 hash 不匹配
3. `RedisMysqlStorageSupport.java:27-36` — `loadAllFromDb()` 从 `super()` 构造器异步提交，子类字段未赋值 → NPE（静默吞没，缓存空）

**设计**：
1. 移除显式 `exists()` 检查，依赖存储层 insert-only 操作（已存在则失败）
2. 将 key 生成移入 `compute()` lambda 内部，通过 `AtomicReference` 返回
3. 将 `loadCache()` 移出构造器，改为 `@PostConstruct` 或子类构造器完成后调用的 `init()` 方法

### 簇 E：生命周期与异步（High × 3）

**问题**：
1. `RedisMysqlStorageSupport.java:40-41` — `gracefulShutdown()` 在预热线程上调用 `awaitTermination(30s)` 等自己 → 必然超时 30 秒
2. `ConfigChangeListenerManager.java:86-87` — `@Async` 需要 `@EnableAsync`，当前缺失 → `triggerListeners` 同步阻塞调用者
3. `AsyncExecutorConfig.java:200-203` — `running` 标志在 `stop()` 中未设为 false → `isRunning()` 误报

**设计**：
1. 移除 `awaitTermination`，仅调用 `shutdown()`（daemon 线程不阻塞 JVM）
2. 在 `AsyncExecutorConfig` 上添加 `@EnableAsync`
3. 在 `stop()` 方法开头添加 `running = false`

### 簇 F：订阅服务修复（High × 1）

**问题**：
- `SubscriptionService.java:64-65` — 长轮询通知返回空 `configs` 列表 → 客户端 `updatePools()` 跳过更新，配置变更延迟一轮

**设计**：
- 从存储层获取完整配置：`configStorage.getAppConfig(appId).ifPresent(config -> deferredResult.setResult(ApiResponse.success(config)))`

### 簇 G：数据完整性 — primitive boolean（Medium × 3）

**问题**：
- `UpdateApiKeyRequest.enabled`、`ApiKeyUpsertCmd.enabled` 使用 primitive `boolean` → 省略时默认 false → 无意禁用 API key
- `ApiKeyAdminService.updateApiKey` 无条件应用 `request.isEnabled()`

**设计**：
- 三处 `boolean enabled` → `Boolean enabled`（包装类型，null 表示未指定）
- `updateApiKey` 中仅当 `request.getEnabled() != null` 时应用，否则保留原值

### 簇 H：实体类 Lombok / MyBatis-Plus 修复（Medium × 8）

**问题**：
- 多个实体缺少 `@NoArgsConstructor`/`@AllArgsConstructor`（MyBatis-Plus 需无参构造器）
- `ApiKeyEntity` 字段遮蔽（`createTime`/`updateTime` 与 `BaseEntity` 重复）
- `ApiKeyHistoryFile`/`ChangeLogEntry` Builder 无 `@Builder.Default` → 时间字段为 null
- `StatsAppDto`/`ThreadPoolStats` 缺少 `@NoArgsConstructor`/`@AllArgsConstructor`

**设计**：
- 统一实体类注解模式：`@Data @NoArgsConstructor @AllArgsConstructor @SuperBuilder`（或 `@Builder`）
- 删除 `ApiKeyEntity` 中重复的 `createTime`/`updateTime` 字段
- `ApiKeyHistoryFile.lastUpdateTime` 和 `ChangeLogEntry.timestamp` 添加 `@Builder.Default` = `LocalDateTime.now()`

### 簇 I：服务层空值防御（Medium × 6）

**问题**：
- `ConfigAdminService.saveConfig/saveConfigs`、`OpenThreadPoolConfigService.addConfig/addConfigs` 缺少参数校验
- `ThreadPoolConfigAppRep.listByAppIds`、`ThreadPoolConfigRep.listByAppIds` 空列表传给 `.in()` → 无效 SQL
- `ThreadPoolConfigRep.findByAppIdAndPoolName` 无 `LIMIT 1` → 多行时 `TooManyResultsException`

**设计**：
- Service 层入口添加 null/blank 校验，抛 `IllegalArgumentException` 或 `ValidationException`
- Repository 层空列表早返回 `List.of()`
- `findByAppIdAndPoolName` 添加 `.last("LIMIT 1")`

### 簇 J：存储层修复（Medium × 8）

**问题**：
- `ThreadPoolStatsPersistenceService` — 用 `updateTime` 而非 `collectTime` 做时间范围查询
- `ConfigStorage.saveConfigs` 默认逐个保存，非原子
- `RedisMysqlApiKeyStorage` — `loadAllFromDb` 静默吞没异常 / `upsert` 失败无感知
- `LocalFileApiKeyStorage` — 手动 copyApiKey 易遗漏字段
- `ConfigChangeListenerManager` — 30 秒 INFO 日志无实质内容
- `LocalFileConfigHistoryStorage` — 不必要双重排序
- `ApiKeyStorage` — 接口级 Logger 导致日志来源混淆

**设计**：
- 统一使用 `collectTime` 做时间范围查询
- `loadAllFromDb` 失败时抛 RuntimeException（fail-fast）
- `upsert` 返回 false 时抛 `StorageException`
- `copyApiKey` 改用 `@Builder(toBuilder = true)` 或 Builder 模式
- 日志降级为 DEBUG + 添加有意义的计数信息

### 簇 K：客户端 SDK 修复（Medium × 8）

**问题**：
- `ThreadPoolAspect.java` — 缺少 `@annotation` 绑定（**已修复**）
- `CreateThreadPoolAspect.java` — 错误计数遗漏（已被 `ThreadPoolAspect` 的 `whenComplete` 模式覆盖）
- `RemoteConfigSourceDetector` — `registerConfigs()` 忽略 API 错误 / `pullConfigsScheduler` 非守护线程
- `ThreadPoolManager.updatePool` — 对未声明池名静默创建（违反声明即使用原则）
- `ThreadPoolProperties` — `pools[]` 缺少 `@Valid` / 缺少跨字段 `core ≤ max` 校验
- `DynamicThreadPoolWrapper.updateConfig` — 非同步，无 `core > max` 防护

**设计**：
- `registerConfigs` 使用 `analyzeResponse()` 解析响应体
- `pullConfigsScheduler` 使用 daemon ThreadFactory
- `updatePool` 对未声明池名抛 `PoolNotFoundException`
- `pools[]` 添加 `@Valid`，`PoolConfig` 添加 `@AssertTrue isPoolSizeValid()`
- `updateConfig` 添加 `synchronized` + core/max 校验

### 簇 L：代码清理与命名（Medium × 10）

**问题**：
- 死代码：`AdminServerAutoConfiguration.configConverter`、`ConfigAdminService.statsStorage`
- 命名：`StatsMapper` → `ThreadPoolStatsMapper`、`apiKeyConvertor` → `apiKeyConverter`
- Javadoc：`ConfigNotModifiedException` 注释写的 404 / `ThreadPoolStatsEntity` 表名注释错误 / `OperateLogEntity` 字段引用错误
- 冗余：`OperateLogEntity.@AllArgsConstructor` 与 `@SuperBuilder` 重复
- POM：6 个文件使用 `0.0.1-SNAPSHOT`

**设计**：
- 删除死代码字段
- 统一命名（重命名 Mapper/字段）
- 修正所有 Javadoc
- 删除冗余注解
- POM 版本号问题留待发布阶段统一处理（不在此阶段修改）

### 簇 M：示例代码修复（Medium × 3）

**问题**：
- `DemoController`（sample-cs-client）默认池名 `default-pool` 在配置中不存在
- `DemoService`（两个 sample）`@AsyncThreadPool` 方法返回 `CompletableFuture` → 嵌套 Future
- `ThreadPoolConfigController` 路径不一致 `/config/` vs `/configs/`

**设计**：
- 默认池名改为配置中实际存在的池名
- 方法返回类型改为 `String`（异步由切面处理）
- 统一路径为 `/configs/`

---

## 2. 交付物清单

**新增**
- 无新模块，全部为现有文件修改

**修改（按簇）**
- 簇 A：`core/ThreadPoolConfig.java`、`admin-server/ThreadPoolConfigMapper.java`
- 簇 B：`admin-server/ThreadPoolConfigPersistenceService.java`、`RedisMysqlConfigStorage.java`
- 簇 C：`admin-server/ApiKeyController.java`、`OpenThreadPoolConfigController.java`、`WebMvcConfig.java`
- 簇 D：`admin-server/ApiKeyAdminService.java`、`LocalFileApiKeyStorage.java`、`RedisMysqlStorageSupport.java`
- 簇 E：`admin-server/RedisMysqlStorageSupport.java`、`ConfigChangeListenerManager.java`、`AsyncExecutorConfig.java`
- 簇 F：`admin-server/SubscriptionService.java`
- 簇 G：`admin-server/UpdateApiKeyRequest.java`、`ApiKeyUpsertCmd.java`、`ApiKeyAdminService.java`
- 簇 H：8 个实体/DTO 文件
- 簇 I：4 个 Service/Repository 文件
- 簇 J：6 个存储层文件
- 簇 K：`client-sdk/RemoteConfigSourceDetector.java`、`ThreadPoolManager.java`、`ThreadPoolProperties.java`、`DynamicThreadPoolWrapper.java`
- 簇 L：10+ 个命名/清理文件
- 簇 M：3 个示例/控制器文件

---

## 3. 与后续阶段的衔接

- 阶段 5 的实体类修复（簇 H）为后续 Micrometer 指标采集提供正确的数据基础
- 安全修复（簇 C）是 admin-server 对外暴露的前置条件
- 版本管理修复（簇 B）确保 CS 模式的配置变更通知链路正确
- 竞态条件修复（簇 D）确保 API key 管理在并发场景下的数据一致性

---

## 4. 验证策略

- 编译验证：`mvn clean compile` 全模块通过
- 现有测试：`mvn test` 全绿
- 手动验证：sample-local 端到端请求测试（ThreadPoolAspect 已验证通过）
- 回归：admin-server 启动无异常，CS 模式长轮询正常
