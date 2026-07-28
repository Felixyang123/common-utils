# idempotent 企业级修缮执行计划

> 状态：待评审。评审通过后按批次执行，每批次独立提交 + 全量单测验证。
> 依据：6 路评审（OCR 62 条 + 5 专项）合并去重后的 21 项清单 + 9 项已拍板决策。
> 词表：`docs/CONTEXT.md`；决策记录：`docs/adr/0003~0005` + `0002` 附注（随对应批次产出）。

---

## 0. 已锁定决策（grill 结论，执行依据）

| # | 决策点 | 结论 |
|---|--------|------|
| D1 | 部署形态 | 多实例为主，Local 仅降级（单实例/测试） |
| D2 | 默认配置 | `storage`/`lock` 默认改 `REDIS`（未配 Redis 启动响亮失败） |
| D3 | 崩溃恢复 | 锁即租约 + 接管（`IdempotentLockProvider` 新增 `isLocked(key)`） |
| D4 | 锁 leaseTime | 默认 60s（看门狗 20s 续期，崩溃检测 ≤60s） |
| D5 | RCE 防护 | fastjson2 SafeMode + 可配包前缀白名单（未配置启动 WARN 不阻断） |
| D6 | 存储故障 | 默认 fail-close，可选 fail-open（ADR-0002 补附注，不推翻） |
| D7 | 装配路线 | 纯自动装配，废弃 `@EnableIdempotent` |
| D8 | FAILED 冷却 | `maxFailRetryCount` 默认 3 + `fail_count` 列 + DDL 版本迁移 |
| D9 | DB 方言 | 仅 MySQL + 文档化（Testcontainers 测，不抽象方言层） |

## 0.1 本次变更的 Breaking 清单（集中到迁移指南）

1. `@EnableIdempotent` 删除 → 启动类改零注解自动装配
2. `@Idempotent.tryLockTime()` 重命名为 `lockLeaseTime()`，默认 3600 → 60
3. `@Idempotent.prefix()` 默认 `"idempotent:"` → `""`
4. 默认 `storage`/`lock`：`LOCAL` → `REDIS`
5. `@Idempotent.keyGenerator()` 语义修正：`key` 非空时不再追加（行为变化，可能影响存量 key 的匹配）

---

## 批次 1：安全红线 + 装配

> 目标：堵住 RCE / SQL 注入 / 越权 / 契约违反；补齐自动装配与依赖治理；落地 D1/D2/D5/D6/D7/D8。
> 产出 ADR-0003（装配与默认配置）、ADR-0005（FAILED 冷却 + DDL 迁移）、ADR-0002 附注（fail-open）。

### 1.1 RCE 防护（D5）

**文件**：`core/IdempotentExecutionManager.java`、`config/IdempotentProperties.java`

- `IdempotentProperties` 新增 `security.resultTypeWhitelist: List<String>`（默认空）。
- 新增私有方法 `assertResultTypeAllowed(String resultType)`：`Class.forName` 前校验 `resultType` 是否以白名单任一包前缀开头；白名单非空且不匹配 → 抛 `IdempotentExecutionException` 并 `log.error`；白名单为空 → 放行但启动时 `log.warn` 提示未配置。
- 反序列化调用改为 `JSON.parseObject(record.getResult(), clz, JSONReader.Feature.SafeMode)`。
- 新增 `IdempotentAutoConfiguration` 启动日志：白名单为空时 WARN「resultType 白名单未配置，存在反序列化风险」。

### 1.2 SQL 注入防护（tableName 白名单）

**文件**：`config/IdempotentProperties.java`

- 类上加 `@Validated`。
- `JdbcConfig.tableName` 加 `@Pattern(regexp = "^[a-zA-Z_][a-zA-Z0-9_]{0,127}$")` + `@NotBlank`。
- 顺带补齐其它配置约束（同时解决评审的校验缺失项）：`expireTime` `@Min(1)`、`local.maxSize` `@Min(1)`、`redis.keyPrefix` `@NotBlank`。
- 引入 `spring-boot-starter-validation`（optional）。

### 1.3 Redis exists() 契约修复

**文件**：`storage/RedisIdempotentStorage.java`

- `exists()`：`hasKey` 返回 `null` 时抛 `IdempotentStorageException`（遵守 ADR-0002「不确定 ≠ false」），不再 `Boolean.TRUE.equals` 吞掉。
- 同步修 `save()` 对 `expireSeconds <= 0` 的防御（抛 `IllegalArgumentException` 或强制最小 1s）。

### 1.4 UserIdKeyGenerator 越权修复

**文件**：`generator/UserIdKeyGenerator.java`

- 用户标识优先取 **认证上下文**（`request.getAttribute("X-User-Id")`，由上游认证过滤器/gateway 写入），HTTP Header 仅作兜底。
- 「匿名回退 `anonymous`」改为可配置：`security.anonymousStrategy = REJECT（默认，抛异常）| ALLOW（共享匿名 key）`。默认 REJECT，避免未认证请求互相误判为重复。

### 1.5 FAILED 冷却 + DDL 版本迁移（D8）

**文件**：`core/IdempotentRecord.java`、`core/IdempotentExecutionManager.java`、`storage/JdbcIdempotentStorage.java`、`storage/LocalIdempotentStorage.java`、`storage/RedisIdempotentStorage.java`、`config/IdempotentTableInitializer.java`、`config/IdempotentProperties.java`、`resources/sql/ddl.sql`

- `IdempotentRecord` 新增 `failCount` 字段。
- `IdempotentProperties` 新增 `maxFailRetryCount = 3`。
- `IdempotentExecutionManager.handleFailRecord`：进入重执行前判断 `failCount >= maxFailRetryCount` → 不再执行，直接按上次错误抛 `IdempotentException`；否则 `failCount++` 后重执行。`saveFailedRecord` 时累加 `failCount`。
- 三端存储持久化 `failCount`：JDBC 加列、Redis（JSON 序列化自动带上）、Local（对象内）。
- **DDL 版本迁移**：`IdempotentTableInitializer` 不再只 `CREATE IF NOT EXISTS`；新增 `checkColumnExists(tableName, "fail_count")`（查 `INFORMATION_SCHEMA.COLUMNS`），缺列则 `ALTER TABLE ... ADD COLUMN fail_count INT DEFAULT 0`。同步更新 `ddl.sql` 与 `buildCreateTableSql`（补 `fail_count`，并补上之前遗漏的 `request_id`，使两条建表路径 schema 一致）。
- `cleanExpiredRecords()` 加 `LIMIT` 分批删除（循环至影响行数 0），并加 `process_status` 索引建议到 DDL。

### 1.6 自动装配与依赖治理（D1/D2/D7）

**文件**：`config/IdempotentAutoConfiguration.java`、`annotation/EnableIdempotent.java`（删）、`annotation/EnableLock`（common-lock，条件化）、`pom.xml`（idempotent + common-lock）、新建 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

- 新建 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`，内容 `com.lezai.idempotent.config.IdempotentAutoConfiguration`。
- 删除 `@EnableIdempotent`；`IdempotentAutoConfiguration` 改为标准自动装配（`@AutoConfiguration`），`@ConditionalOnBooleanProperty(idempotent.enabled, matchIfMissing=true)` 保持。
- 默认配置：`IdempotentProperties.storage` 默认 `StorageStrategy.REDIS`、`lock` 默认 `LockStrategy.REDIS`。
- **大小写宽松**：`@ConditionalOnProperty(havingValue="REDIS"/"JDBC")` 改为基于绑定后枚举的自定义条件（`AnyNestedCondition` 或 `@ConditionalOnExpression` 读取 `IdempotentProperties`），避免配小写 `redis`/`jdbc` 静默 fallback。
- **@ConditionalOnClass**：Redis 相关 Bean 加 `@ConditionalOnClass(RedisConnectionFactory.class)`，JDBC 加 `@ConditionalOnClass(DataSource.class)`，缺依赖时给出明确 WARN 而非 `UnsatisfiedDependencyException`。
- **optional 依赖**：`caffeine`、`fastjson2`、`common-lock` 在 pom 中标 `<optional>true</optional>`，Local/Redis 存储 Bean 上加 `@ConditionalOnClass`。
- **`@EnableLock` 条件化**：从 `IdempotentAutoConfiguration` 类上移除硬编码 `@EnableLock`，改为仅在 `idempotent.lock=REDIS` 时通过 `@ConditionalOnProperty` 的内部配置类导入；`common-lock` 在 pom 标 optional。
- 更新 `CommonUtilsApplication`：移除 `@EnableIdempotent`。

### 1.7 批次 1 测试

- 新增：`assertResultTypeAllowed` 白名单通过/拒绝用例；`exists()` null 上抛用例；`UserIdKeyGenerator` 认证上下文/匿名 REJECT/ALLOW 用例；FAILED 达冷却上限不重执行用例；DDL 缺列自动 ALTER 用例。
- 更新：`IdempotentExecutionManagerTest`、`JdbcIdempotentStorageTest`、`RedisIdempotentStorageTest`（适配新行为与默认值）。
- 验证：`mvnw -pl idempotent test` 全绿。

---

## 批次 2：并发正确性

> 目标：修复幂等击穿的真正残留、泛型结果、key 生成；落地 D3/D4。
> 产出 ADR-0004（锁即租约崩溃接管 + leaseTime=60s）。
> **依赖**：批次 1 已完成（冷却的 `failCount`、装配默认值已就位）。

### 2.1 rolling TTL（三端语义统一）

**文件**：`core/IdempotentExecutionManager.java`、`storage/JdbcIdempotentStorage.java`、`storage/LocalIdempotentStorage.java`

- `saveSuccessRecord` / `saveFailedRecord` 在 `storage.save` 前重设 `record.setExpireTime(now + expireSeconds)`，使终态记录从「状态最后更新」起算。
- `JdbcIdempotentStorage.save` 保持用 `record.getExpireTime()`（现在已是正确的 rolling 值）。
- `LocalIdempotentStorage.save` 忽略 `expireSeconds` 参数的问题：改为按 `expireSeconds` 用 Caffeine 的 `expireAfterWrite` 变体（或 `Cache.policy().expireVariably`），不再用构造期固定 TTL；文档化三端 TTL 语义一致。

### 2.2 锁即租约 + 崩溃接管（D3）

**文件**：`lock/IdempotentLockProvider.java`、`lock/LocalLockProvider.java`、`lock/RedisLockProvider.java`、`core/IdempotentExecutionManager.java`

- 接口新增 `boolean isLocked(String key)`（是否被任意线程/实例持有，用于判定执行者存活）。
  - `LocalLockProvider`：`lockMap` 中存在 entry 即视为持有。
  - `RedisLockProvider`：委托 `RedisDistributeLock` 新增「锁 key 是否存在」能力（`common-lock` 加只读方法，非 `heldByCurrentThread`）。
- `IdempotentExecutionManager.handleProcessingRecord`：先查 `isLocked(key)`——
  - 锁已死（执行者崩溃）→ 走**接管**：获取锁 → 双检 → 将该 PROCESSING 记录转 FAILED → 按 FAILED 流程重执行（复用批次 1 的冷却判断）。
  - 锁仍活（执行者健在）→ 维持原 fail-fast / wait-retry。
- 注解：`@Idempotent.tryLockTime()` → `lockLeaseTime()`，默认 `3600` → `60`；注释改为「锁租约时长（秒），由看门狗续期，崩溃检测灵敏度」。`IdempotentExecutionManager` 同步改用 `lockLeaseTime()`。

### 2.3 泛型结果正确反序列化

**文件**：`core/IdempotentExecutionManager.java`

- 成功时 `resultType` 存**完整泛型签名**（`result.getClass().getGenericSuperclass()` 不适用——改为用 `com.alibaba.fastjson2.TypeReference` 捕获真实类型 / 存 `method.getGenericReturnType()` 的字符串）。
- 重复请求反序列化用 `JSON.parseObject(result, Type)`（`Type` 由存的签名还原，如 `List<Order>`），而非 `Class`。
- **存量兼容**：旧记录若只存了 raw class 名，fallback 到 `Class.forName` 路径。
- **防御**：`resultType` 为 null（`void` 方法或 `storeResult=false`）时不再 `Class.forName(null)`；`void` 方法 `returnResultOnDuplicate` 直接返回 null，`storeResult=false` 的组合按「重复」抛异常。
- 反序列化失败保留原始 cause（`throw new IdempotentExecutionException(msg, e)`）。

### 2.4 keyGenerator 语义修正

**文件**：`core/IdempotentKeyResolver.java`、`generator/DefaultKeyGenerator.java`

- `key` 非空时**跳过** `keyGenerator`（与注解文档「key 为空时使用此生成器」一致）。
- 判定「显式指定生成器」：`keyGenerator() != DefaultKeyGenerator.class` 时才执行生成器追加（消除默认双 key 拼接）。
- `parsedKey` 为 null 时不拼字面量 `null`（fallback 到默认键或抛明确异常）。
- `Arrays.toString` → `Arrays.deepToString`（数组参数 hash 稳定）。
- MD5 取全 32 位（去掉 `substring(0,8)`），消除 32bit 生日碰撞。
- 消除 `IdempotentKeyResolver.buildDefaultKey` 与 `DefaultKeyGenerator.generate` 的重复逻辑（委托一处）。

### 2.5 common-lock 看门狗修复

**文件**：`common-lock/.../WatchDogExecutor.java`

- `isRunning()` 返回 `!isShutdown.get()`（当前返回 `isShutdown.get()`，逻辑反向导致 Spring 关闭时不调 `stop()`，续约线程泄漏为 daemon）。
- 评审续约线程模型（单线程 DelayQueue），必要时改调度线程池。

### 2.6 批次 2 测试

- 新增：rolling TTL 三端一致用例；`isLocked` 崩溃接管（锁死→重执行、锁活→fail-fast/wait）用例；泛型 `List<Order>` 缓存结果还原用例；存量 raw class fallback 用例；keyGenerator 不追加/显式指定才追加用例；`deepToString` 数组参数一致用例。
- 更新：`IdempotentExecutionManagerTest`（PROCESSING 场景 mock 需补 `isLocked` 桩）、`IdempotentKeyResolverTest`、`LocalIdempotentStorageTest`。
- 验证：`mvnw -pl idempotent test` + `mvnw -pl common-lock test` 全绿。

---

## 批次 3：可观测 + 测试 + 文档

> 目标：生产可运维；公共 API 命名收尾；集成测试与文档。落地迁移指南。
> **依赖**：批次 1/2 完成。

### 3.1 Micrometer Metrics

**文件**：`core/IdempotentExecutionManager.java`、`config/IdempotentAutoConfiguration.java`、`pom.xml`

- `MeterRegistry` 以 `@Autowired(required=false)`/optional 注入，缺 micrometer 不报错。
- 埋点：
  - `idempotent.request.total`（Counter，总请求）
  - `idempotent.request.duplicate`（Counter，tag `status=SUCCEEDED/FAILED/PROCESSING`）
  - `idempotent.request.takeover`（Counter，崩溃接管次数）
  - `idempotent.execute.duration`（Timer，业务执行耗时）
  - `idempotent.lock.wait.duration`（Timer，锁等待耗时）
- pom 加 `micrometer-core`（optional）。

### 3.2 结构化日志 + requestId 激活 + HTTP 状态码

**文件**：`core/IdempotentExecutionManager.java`、`core/IdempotentRecord.java`、`aspect/IdempotentAspect.java`、`exception/*`、新建 `config/IdempotentExceptionHandler.java`

- 日志统一 `key= method= status= duration=` 格式，便于 ELK/Loki 解析。
- `requestId` 在 `IdempotentAspect` 从 MDC（`traceId`，无则生成 UUID）写入 `IdempotentRecord.requestId`；JDBC 存储补 `request_id` 的写入与 RowMapper 读取（与批次 1 的 DDL 补列呼应）。
- 新建 `@ControllerAdvice` `IdempotentExceptionHandler`：`IdempotentException`→**409**、`IdempotentLockException`→**429 + Retry-After**、`IdempotentStorageException`→**503 + Retry-After**（**补齐 ADR-0002 承诺但未落地的部分**）。
- `IdempotentAspect` 异常日志分级：幂等预期异常（重复/处理中/锁失败）降为 `log.warn`，非预期才 `log.error`，消除生产误报。

### 3.3 公共 API 命名收尾（破坏性，并入迁移指南）

**文件**：`annotation/Idempotent.java`

- `failFast` 注释修正（语义=快速失败不重试，当前注释「是否需要重试」相反）。
- `maxRetryCount`/`retryInterval` 注释引用不存在的 `needRetry` → 改为 `failFast=false`。
- `prefix` 默认 `"idempotent:"` → `""`（消除 `idempotent:idempotent:` 双前缀；全局前缀由 `redis.keyPrefix` 承担）。
- `retryInterval` 加非负防御（`Math.max(0, ...)` 或 `@PositiveOrZero`）。

### 3.4 组合参数校验

**文件**：`annotation/Idempotent.java`（文档）、`aspect/IdempotentAspect.java` 或 `core/IdempotentExecutionManager.java`

- 校验非法组合：`storeResult=false && returnResultOnDuplicate=true` → 启动/首次使用时给出明确错误或告警；`void` 方法 + `returnResultOnDuplicate=true` → 容忍（返回 null）。

### 3.5 集成测试 + 文档

**文件**：`src/test/...`（新建）、`idempotent/docs/README.md`（新建）、`docs/`（迁移指南）

- `@SpringBootTest` 自动装配验证：默认 Redis 装配、缺依赖 WARN、大小写宽松、optional 降级。
- Testcontainers MySQL：JDBC 存储端到端（建表、rolling TTL、冷却、崩溃接管、清理分批）。
- `idempotent/docs/README.md`：启用方式（零注解自动装配）、配置项、三端差异、异常→HTTP 映射、已知限制（仅 MySQL、at-least-once 边界、同类自调用失效）。
- `docs/migration-idempotent.md`：Breaking 清单（见 0.1）逐条迁移示例。
- 更新 `docs/CONTEXT.md` 词表（若引入新术语）。

### 3.6 批次 3 验证

- `mvnw -pl idempotent test` + `mvnw -pl common-lock test` 全绿 + `@SpringBootTest` 集成测试通过。

---

## 缓办（明确不在本次范围）

- `AbstractProcessor` 编译期 SpEL 校验
- JMH 压测基准
- 数据库方言抽象（PostgreSQL 等）——已决策仅 MySQL
- `LocalLockProvider` 无自动过期——残存风险仅限 JVM 崩溃（此时整个 map 随进程消失），TODO 已注明，不动
- `ProcessingStrategy` / `StorageStrategy.name` / 死代码清理（随批次 3 顺手，不单列）

---

## 执行与提交约定

1. 每批次完成后跑全量单测，绿则**独立提交**（commit message 遵循仓库 `type(scope): 中文描述` 风格）。
2. ADR 随对应批次一并提交（批次 1：ADR-0003/0005/0002 附注；批次 2：ADR-0004）。
3. 批次间不并行，按 1 → 2 → 3 顺序，因为 2 依赖 1 的冷却字段与默认值，3 依赖 1/2 的稳定 API。
4. 每批次结束向你汇报变更点与测试结果，确认后再进下一批次。

## 风险与注意

- **isLocked 引入的测试影响面**：现有 `IdempotentExecutionManagerTest` 大量 mock PROCESSING 场景，`isLocked` 默认 mock 返回 false 会误走接管路径，需逐条补桩（批次 2 重点）。
- **keyGenerator 行为变化**可能影响存量 key 匹配——已列入 Breaking，迁移指南需提示「升级后新旧 key 不匹配，等效于缓存全失效一次」。
- **默认改 Redis**：未引 Redis 的服务启动即失败——这是 D2 的有意设计（响亮失败优于静默失效），README 需醒目说明。
