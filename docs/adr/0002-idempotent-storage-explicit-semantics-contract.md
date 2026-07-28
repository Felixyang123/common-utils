# ADR-0002: IdempotentStorage 接口契约 —— "null ≠ 异常"

## 状态

accepted (2026-07-19)

## 背景与决策

`IdempotentStorage` 的三个实现类(Local / Redis / JDBC)在 593d99f / 13ac823 / 6247b1e 等提交中加了防御性 try-catch,并在捕获异常后返回 `null`(get)或 `false`(exists)。这种行为的语义是"异常 == 无记录",导致 `IdempotentExecutionManager.execute()` 在存储不可用时错误地放行请求,与真实正在处理的并发请求发生业务层竞态,幂等性失效。

我们决定 **`IdempotentStorage` 建立明确接口契约**:所有实现类的 `get()` 方法必须 **"返回 null === 唯一真实的'无记录'语义"**,Redis 或 JDBC 等 IO 异常必须 **上抛调用方**,不允许被 catch 后包装为 null。调用方 (`IdempotentExecutionManager.execute()`) 在收到存储异常时 fail-fast,抛 `IdempotentStorageException`,AOP 切面映射为 **HTTP 503 + Retry-After 头**,客户端安全重试。

## 关键理由

| 维度 | 分析 |
|------|------|
| 为什么 null 比异常宽松 | 真实"无记录"路径上,ConcurrentHashMap / Caffeine / RDBMS 不存在 IO 异常,不会出现 null/exception 语义混淆。传入异常伪装成 null,等于让调用方在"存储健康但缺数据"和"存储是否可用都不清楚"两种情况走同一路径,幂等上层在第二种情况无能为力 |
| 为什么不返回 `Optional` / 包装类型 | 接口契约 + 上抛异常方案改动面更小(仅 get 去掉 try,exists 走 Redis 原生 hasKey + 异常上抛);调用方 catch 是上策,返回包装类型需要接口签名变更,然后调用栈沿途都要改 |
| 为什么上抛 + 503 比 null 更安全 | 客户端重试可见:503 + Retry-After 明确告诉客户端"这次不知道,等一下再来"。null 放行意味着客户端已经进入了业务代码,重试就晚了 |
| exists() 也要上抛 | 异常时 exists 返回 false,调用方就认为"key 不存在" → 进入 `handleConcurrentRequest` → create 新 record → 与真实正在处理的请求并发 → 同 get 失效。所以 exists 不能单独偷懒 |

## 决策影响

- **实现类联动修改**:`RedisIdempotentStorage` / `JdbcIdempotentStorage` 的 `get()` / `remove()` / `exists()` 必须去掉 try-catch,异常上抛。`LocalIdempotentStorage` 不需要 IO 异常防护,本约定对其影响为 **禁止**引入防御性 try
- **接口 Javadoc 变更**:`IdempotentStorage.get()` 加 JavaDoc "@throws StorageException 存储访问异常",contract 正式文档化
- **调用方兜底策略**:`IdempotentExecutionManager.execute()` 把 `storage.get(key)` 置于 try 中,任何异常转换为 `IdempotentStorageException`;HTTP 切面(`@ControllerAdvice`)把 `IdempotentStorageException` 映射为 503 + Retry-After(默认 1s)
- **配套设计**:此约定 + ADR-0001(LocalLockProvider 真原子化)共同构成"存储层与锁层双保险":锁层保证互斥(true mutex via CHM.compute),存储层保证语义清晰(null means no data ONLY)

## 权衡与后果

- **可用性 vs 一致性**:上抛异常返回 503 会降低存储抖动时的可用性(用户看到错误,但业务代码不会重复执行);alternative 的"null=放行"会增加可用性(用户成功)但破坏一致性(同 key 并发执行业务)。**幂等控制中一致性优先于可用性**
- **客户端适配**:调用方客户端需要正确处理 503 + Retry-After 头,自动重试;不处理的客户端会看到错误或多次重复请求被 503 阻塞,但代码层面不破坏
- **与现有测试兼容性**:现有测试中 mock 的 `storage.get(...)` 返回 null 场景仍然保留(真实无记录测试),新增 503 retry 测试覆盖"存储异常 + 客户端重试"的恢复路径

## 相关文件

- `idempotent/src/main/java/com/lezai/idempotent/storage/IdempotentStorage.java` (接口 Javadoc)
- `idempotent/src/main/java/com/lezai/idempotent/storage/RedisIdempotentStorage.java`
- `idempotent/src/main/java/com/lezai/idempotent/storage/JdbcIdempotentStorage.java`
- `idempotent/src/main/java/com/lezai/idempotent/storage/LocalIdempotentStorage.java`
- `idempotent/src/main/java/com/lezai/idempotent/core/IdempotentExecutionManager.java` (加 try-catch 兜底)

---

## 增补：可选 fail-open 逃生口 (2026-07-26)

### 背景

原始决策坚持「一致性优先于可用性」（fail-close），存储异常一律 503。在实践中，部分高可用服务在 Redis 短暂抖动时更倾向于放行请求（接受极端窗口下的重复执行风险）而非全局 503。

### 增补决策

保留 fail-close 为**默认策略**（遵守原决策），新增 `idempotent.storage-fail-open=false` 配置项作为可选逃生口：

- `false`（默认）：存储异常 → `IdempotentStorageException` → 503 + Retry-After（原行为）
- `true`：存储异常 → `log.error` + 放行请求（`record = null`，走首次执行路径）

### 影响

- 不推翻原决策——默认行为不变，fail-open 需显式开启
- fail-open 时业务方法会被执行，但幂等记录未写入——相同请求在存储恢复前可能重复执行
- 仅建议在「可用性绝对优先 + 业务自身有独立幂等保障（如数据库唯一约束）」的场景下启用

### 相关文件

- `idempotent/src/main/java/com/lezai/idempotent/config/IdempotentProperties.java`（新增属性，待实现）
- `idempotent/src/main/java/com/lezai/idempotent/core/IdempotentExecutionManager.java`（execute 方法 catch 分支，待实现）
