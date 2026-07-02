# 阶段 5 · 代码审查加固 · 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: subagent-driven-development.

**Goal:** 修复 open-code-review 发现的 18 个 High + 65 个 Medium 问题，覆盖编译阻塞、SQL 错误、安全漏洞、竞态条件、数据完整性、实体类、空值防御、存储层、客户端 SDK 和代码清理。

**Architecture:** 13 个修复簇（A–M），详见 spec `2026-06-27-stage5-code-review-hardening-design.md`。

**Dependency:** 阶段 1–4 已完成；ThreadPoolAspect `@annotation` 绑定修复已在本轮完成（`882751f`）。

---

## File Structure

```
common-thread-pool/
├── core/src/main/java/com/lezai/threadpool/
│   └── bean/ThreadPoolConfig.java                   ← MODIFY (移除 @Builder)
├── admin-server/src/main/java/com/lezai/threadpool/
│   ├── controller/
│   │   ├── ApiKeyController.java                    ← MODIFY (安全：加认证)
│   │   ├── ThreadPoolConfigController.java          ← MODIFY (路径一致性 + poolName 校验)
│   │   └── dto/request/UpdateApiKeyRequest.java     ← MODIFY (boolean → Boolean)
│   ├── open/OpenThreadPoolConfigController.java     ← MODIFY (@Max + @Validated)
│   ├── config/
│   │   ├── AdminServerAutoConfiguration.java        ← MODIFY (删死代码)
│   │   └── AsyncExecutorConfig.java                 ← MODIFY (stop running=false + @EnableAsync)
│   ├── service/
│   │   ├── ConfigAdminService.java                  ← MODIFY (删死代码 + 空值校验)
│   │   ├── ApiKeyAdminService.java                  ← MODIFY (竞态 + Boolean)
│   │   ├── SubscriptionService.java                 ← MODIFY (返回完整配置)
│   │   ├── OpenThreadPoolConfigService.java         ← MODIFY (空值校验)
│   │   ├── ThreadPoolConfigPersistenceService.java  ← MODIFY (版本号修复)
│   │   └── ThreadPoolStatsPersistenceService.java   ← MODIFY (collectTime + 分页)
│   ├── dao/
│   │   ├── entity/
│   │   │   ├── ApiKeyEntity.java                    ← MODIFY (删重复字段 + @NoArgsConstructor)
│   │   │   ├── ThreadPoolConfigEntity.java          ← MODIFY (@NoArgsConstructor)
│   │   │   ├── ThreadPoolStatsEntity.java           ← MODIFY (@NoArgsConstructor + Javadoc)
│   │   │   └── OperateLogEntity.java                ← MODIFY (删冗余 @AllArgsConstructor + Javadoc)
│   │   ├── mapper/
│   │   │   ├── ThreadPoolConfigMapper.java          ← MODIFY (@MapKey + foreach)
│   │   │   └── StatsMapper.java                     ← RENAME → ThreadPoolStatsMapper
│   │   └── rep/
│   │       ├── ThreadPoolConfigRep.java             ← MODIFY (LIMIT 1 + 空列表防护)
│   │       └── ThreadPoolConfigAppRep.java          ← MODIFY (空列表防护)
│   ├── pojo/
│   │   ├── cmd/ApiKeyUpsertCmd.java                 ← MODIFY (boolean → Boolean)
│   │   └── dto/StatsAppDto.java                     ← MODIFY (@NoArgsConstructor)
│   ├── interceptor/
│   │   └── RateLimitInterceptor.java                ← MODIFY (空 appId 防护)
│   └── storage/
│       ├── ApiKeyStorage.java                       ← MODIFY (接口 Logger)
│       ├── ConfigStorage.java                       ← MODIFY (saveConfigs 原子性注释)
│       ├── ConfigHistoryStorage.java                ← MODIFY (排序一致性)
│       ├── listener/ConfigChangeListenerManager.java ← MODIFY (日志级别 + @EnableAsync)
│       ├── localfile/
│       │   ├── LocalFileApiKeyStorage.java           ← MODIFY (竞态 + copyApiKey)
│       │   ├── LocalFileApiKeyHistoryStorage.java    ← MODIFY (消除双重排序)
│       │   └── LocalFileConfigHistoryStorage.java    ← MODIFY (排序)
│       └── remote/
│           ├── RedisMysqlStorageSupport.java         ← MODIFY (shutdown + 构造器竞态)
│           ├── RedisMysqlConfigStorage.java          ← MODIFY (版本传递 + NPE)
│           └── RedisMysqlApiKeyStorage.java          ← MODIFY (异常处理)
├── client-sdk/src/main/java/com/lezai/threadpool/
│   ├── client/RemoteConfigSourceDetector.java       ← MODIFY (registerConfigs 解析响应)
│   ├── manager/ThreadPoolManager.java               ← MODIFY (updatePool fail-fast)
│   ├── properties/ThreadPoolProperties.java         ← MODIFY (@Valid + @AssertTrue)
│   └── core/DynamicThreadPoolWrapper.java           ← MODIFY (synchronized + core>max 校验)
└── samples/
    ├── sample-local/.../DemoService.java            ← MODIFY (返回 String)
    └── sample-cs-client/
        ├── .../DemoController.java                  ← MODIFY (默认池名)
        └── .../DemoService.java                     ← MODIFY (返回 String)
```

---

## 执行策略

计划按 **3 个 batch** 执行，每个 batch 内部可并行，batch 间串行（有依赖）。

---

### Batch 1：编译阻塞 + 安全 + 生命周期（簇 A/C/E）

> 这些是"不修就不能用"的问题。

---

#### Task 1.1: 簇 A — 编译/SQL 修复

**Files:** `ThreadPoolConfig.java`, `ThreadPoolConfigMapper.java`

- [ ] **Step 1: 修 ThreadPoolConfig — 移除 @Builder**

打开 `core/src/main/java/com/lezai/threadpool/bean/ThreadPoolConfig.java`。

将类注解 `@Builder` 替换为:
```java
@AllArgsConstructor(access = AccessLevel.PACKAGE)
```
保留手动 `ThreadPoolConfigBuilder` 内部类和 `builder()` 方法不变。需添加 import:
```java
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
```

- [ ] **Step 2: 修 ThreadPoolConfigMapper — @MapKey + foreach**

打开 `admin-server/src/main/java/com/lezai/threadpool/dao/mapper/ThreadPoolConfigMapper.java`。

A. `countAllByAppId()` 方法：添加 `@MapKey("app_id")` 注解，返回类型改为 `Map<String, Map<String, Object>>`:
```java
@MapKey("app_id")
@Select("SELECT app_id, COUNT(*) AS count FROM thread_pool_config WHERE deleted = 0 GROUP BY app_id")
Map<String, Map<String, Object>> countAllByAppId();
```

B. `countByAppIds()` 方法（如有类似签名）：同样改为 `<script>` + `<foreach>`:
```java
@Select("<script>SELECT app_id, COUNT(*) AS count FROM thread_pool_config WHERE deleted = 0 AND app_id IN "
        + "<foreach collection='appIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> "
        + "GROUP BY app_id</script>")
@MapKey("app_id")
Map<String, Map<String, Object>> countByAppIds(@Param("appIds") List<String> appIds);
```

- [ ] **Step 3: 编译验证**

```bash
cd /d/code/common-utils/common-thread-pool
mvn -pl core,admin-server -am clean compile -DskipTests
```
期望: BUILD SUCCESS

---

#### Task 1.2: 簇 C — 安全修复

**Files:** `ApiKeyController.java`, `OpenThreadPoolConfigController.java`, `WebMvcConfig.java`

- [ ] **Step 1: OpenThreadPoolConfigController — @Max + @Validated**

打开 `admin-server/.../open/OpenThreadPoolConfigController.java`。

A. 类上添加 `@Validated`:
```java
@Slf4j
@RestController
@RequestMapping("/open/api/thread-pool")
@RequiredArgsConstructor
@Validated
```

B. `subscribe` 方法的 `timeout` 参数添加 `@Max`:
```java
@Valid
@Min(value = 1000, message = "timeout必须在1000-60000之间")
@Max(value = 60000, message = "timeout必须在1000-60000之间")
@RequestParam(defaultValue = "30000")
Long timeout
```

- [ ] **Step 2: ApiKeyController — 加认证**

打开 `admin-server/.../config/WebMvcConfig.java`，在 `addInterceptors` 中添加 `/api/api-keys/**` 路径到认证拦截器:
```java
registry.addInterceptor(apiKeyAuthInterceptor)
        .addPathPatterns("/open/api/thread-pool/**", "/api/api-keys/**");
```

- [ ] **Step 3: 编译 + 测试**

```bash
mvn -pl admin-server -am clean test -Dfile.encoding=UTF-8
```

---

#### Task 1.3: 簇 E — 生命周期修复

**Files:** `RedisMysqlStorageSupport.java`, `AsyncExecutorConfig.java`

- [ ] **Step 1: RedisMysqlStorageSupport — 移除自阻塞**

打开 `admin-server/.../storage/remote/RedisMysqlStorageSupport.java`。

A. `gracefulShutdown()` 中移除 `awaitTermination`，只保留 `shutdown()`:
```java
private void gracefulShutdown() {
    warmupExecutor.shutdown();
}
```

B. 构造器竞态修复：将 `loadCache()` 调用从构造器移至 `@PostConstruct`:
```java
// 构造器中删除 loadCache() 调用
@PostConstruct
public void init() {
    loadCache();
}
```

- [ ] **Step 2: AsyncExecutorConfig — stop + @EnableAsync**

A. 类上添加 `@EnableAsync`:
```java
@Configuration
@EnableAsync
public class AsyncExecutorConfig {
```

B. `stop()` 方法开头添加 `running = false`:
```java
@Override
public void stop() {
    running = false;
    ExecutorUtils.shutdown(subscriptionExecutor, "long polling subscription");
}
```

- [ ] **Step 3: 编译 + 测试**

```bash
mvn -pl admin-server -am clean test -Dfile.encoding=UTF-8
```

---

### Batch 2：竞态条件 + 数据完整性 + 版本管理（簇 B/D/F/G）

> 这些是"数据会出错"的问题。

---

#### Task 2.1: 簇 B — 版本管理修复

**Files:** `ThreadPoolConfigPersistenceService.java`, `RedisMysqlConfigStorage.java`

- [ ] **Step 1: addConfigApp 版本递增**

打开 `admin-server/.../service/ThreadPoolConfigPersistenceService.java`，找到 `addConfigApp` 方法中:
```java
configApp.setVersion(configApp.getVersion());
```
改为:
```java
configApp.setVersion(configApp.getVersion() + 1);
```

- [ ] **Step 2: deleteByAppIdAndPoolName 空池防护**

同文件，找到 `deleteByAppIdAndPoolName` 方法。将:
```java
configOptional.ifPresent(config -> {
    configRep.removeById(config.getId());
    log(List.of(config), OperateType.DELETE);
});
```
改为:
```java
if (configOptional.isEmpty()) {
    return configConverter.convertDto(configApp);
}
configRep.removeById(configOptional.get().getId());
log(List.of(configOptional.get()), OperateType.DELETE);
```

- [ ] **Step 3: RedisMysqlConfigStorage 版本传递 + NPE 防护**

打开 `admin-server/.../storage/remote/RedisMysqlConfigStorage.java`。

A. `addAndRefreshConfigs` 中合并缓存时同步版本:
```java
} else {
    oldConfigAppDto.setVersion(configAppDto.getVersion());
    oldConfigAppDto.getConfigs().putAll(configAppDto.getConfigs());
}
```

B. `addConfig`、`addConfigs` 方法中 `compute()` 结果添加 null 检查:
```java
if (currentConfigAppDto == null) {
    throw new StorageException("Failed to add config for appId: " + appId);
}
```

C. `doRefresh` 中 `getConfigs()` 添加 null 检查:
```java
|| currentConfigAppDto.getConfigs() == null
```

- [ ] **Step 4: 编译 + 测试**

```bash
mvn -pl admin-server -am clean test -Dfile.encoding=UTF-8
```

---

#### Task 2.2: 簇 D — 竞态条件修复

**Files:** `ApiKeyAdminService.java`, `LocalFileApiKeyStorage.java`

- [ ] **Step 1: ApiKeyAdminService — 移除 check-then-act**

打开 `admin-server/.../service/ApiKeyAdminService.java`，找到 `createApiKey` 方法。移除显式 `exists()` 检查，让存储层的 upsert 失败来处理重复（或改为 insert-only）。

- [ ] **Step 2: LocalFileApiKeyStorage — regenerateApiKey 竞态修复**

打开 `admin-server/.../storage/localfile/LocalFileApiKeyStorage.java`，找到 `regenerateApiKey` 方法。将 key 生成移入 `compute()` lambda 内部，通过 `AtomicReference` 返回。

- [ ] **Step 3: 编译 + 测试**

```bash
mvn -pl admin-server -am clean test -Dfile.encoding=UTF-8
```

---

#### Task 2.3: 簇 F/G — 订阅服务 + boolean 修复

**Files:** `SubscriptionService.java`, `UpdateApiKeyRequest.java`, `ApiKeyUpsertCmd.java`, `ApiKeyAdminService.java`

- [ ] **Step 1: SubscriptionService — 返回完整配置**

打开 `admin-server/.../service/SubscriptionService.java`，找到监听器回调。将:
```java
deferredResult.setResult(ApiResponse.success(ThreadPoolAppConfig.builder().appId(appId)
        .configVersion(newVersion).build()));
```
改为:
```java
configStorage.getAppConfig(appId).ifPresent(config ->
        deferredResult.setResult(ApiResponse.success(config)));
```

- [ ] **Step 2: boolean → Boolean**

A. `UpdateApiKeyRequest.java`: `boolean enabled` → `Boolean enabled`
B. `ApiKeyUpsertCmd.java`: `boolean enabled` → `Boolean enabled`
C. `ApiKeyAdminService.updateApiKey`: 条件应用:
```java
.enabled(request.getEnabled() != null ? request.getEnabled() : existing.isEnabled())
```

- [ ] **Step 3: 编译 + 测试**

```bash
mvn -pl admin-server -am clean test -Dfile.encoding=UTF-8
```

---

### Batch 3：实体类 + 服务层 + 存储层 + 客户端 SDK + 清理（簇 H/I/J/K/L/M）

> 这些是"代码质量"问题，不影响核心功能但影响健壮性。

---

#### Task 3.1: 簇 H — 实体类 Lombok 修复

**Files:** 8 个实体/DTO 文件

- [ ] **Step 1: 批量添加 @NoArgsConstructor/@AllArgsConstructor**

为以下类添加缺失的构造器注解（对齐项目惯例 `@Data @NoArgsConstructor @AllArgsConstructor @SuperBuilder`）:
- `ThreadPoolConfigEntity` — 添加 `@NoArgsConstructor @AllArgsConstructor`
- `ThreadPoolStatsEntity` — 添加 `@NoArgsConstructor`
- `ApiKeyEntity` — 添加 `@NoArgsConstructor @AllArgsConstructor` + 删除重复的 `createTime`/`updateTime` 字段
- `StatsAppDto` — 添加 `@NoArgsConstructor @AllArgsConstructor`
- `ThreadPoolStats` — 添加 `@NoArgsConstructor @AllArgsConstructor`

- [ ] **Step 2: @Builder.Default 修复**

- `ApiKeyHistoryFile.lastUpdateTime` — 添加 `@Builder.Default` + `= LocalDateTime.now()`
- `ChangeLogEntry.timestamp` — 添加 `@Builder.Default` + `= LocalDateTime.now()`

- [ ] **Step 3: Javadoc 修正**

- `ThreadPoolStatsEntity` — `对应数据库表 stats_storage` → `对应数据库表 thread_pool_stats`
- `OperateLogEntity.bizId` — `logType/logId` → `bizType/bizId`
- `ConfigNotModifiedException` — `配置不存在异常 404` → `配置未修改异常 304`

- [ ] **Step 4: 冗余注解**

- `OperateLogEntity` — 删除 `@AllArgsConstructor`（`@SuperBuilder` 已生成）

- [ ] **Step 5: 编译验证**

```bash
mvn -pl admin-server,core -am clean compile -DskipTests
```

---

#### Task 3.2: 簇 I — 空值防御

**Files:** `ConfigAdminService.java`, `OpenThreadPoolConfigService.java`, `ThreadPoolConfigRep.java`, `ThreadPoolConfigAppRep.java`

- [ ] **Step 1: Service 层校验**

在 `ConfigAdminService.saveConfig/saveConfigs`、`OpenThreadPoolConfigService.addConfig/addConfigs` 入口添加 null/blank 校验。

- [ ] **Step 2: Repository 层防护**

A. `ThreadPoolConfigAppRep.listByAppIds` — 空列表早返回
B. `ThreadPoolConfigRep.listByAppIds` — 空列表早返回
C. `ThreadPoolConfigRep.findByAppIdAndPoolName` — 添加 `.last("LIMIT 1")`

- [ ] **Step 3: 编译 + 测试**

---

#### Task 3.3: 簇 J — 存储层修复

**Files:** `ThreadPoolStatsPersistenceService.java`, `RedisMysqlApiKeyStorage.java`, `LocalFileApiKeyStorage.java`, `ConfigChangeListenerManager.java`, `LocalFileApiKeyHistoryStorage.java`, `ApiKeyStorage.java`, `ConfigHistoryStorage.java`

- [ ] **Step 1: 时间字段修复**

`ThreadPoolStatsPersistenceService` — `between(ThreadPoolStatsEntity::getUpdateTime, ...)` → `between(ThreadPoolStatsEntity::getCollectTime, ...)`，并添加分页限制 `.last("LIMIT 1000")`

- [ ] **Step 2: 异常处理**

`RedisMysqlApiKeyStorage.loadAllFromDb` — catch 中添加 `throw new RuntimeException("API key cache warmup failed", e)`

- [ ] **Step 3: 日志降级**

`ConfigChangeListenerManager` — `log.info("Clean expired listeners")` → `log.debug("Scanning for expired listeners...")`

- [ ] **Step 4: 编译 + 测试**

---

#### Task 3.4: 簇 K — 客户端 SDK 修复

**Files:** `RemoteConfigSourceDetector.java`, `ThreadPoolManager.java`, `ThreadPoolProperties.java`, `DynamicThreadPoolWrapper.java`

- [ ] **Step 1: registerConfigs 解析响应**

`RemoteConfigSourceDetector.registerConfigs` — 用 `analyzeResponse()` 替代 `response.isSuccessful()` 检查

- [ ] **Step 2: updatePool fail-fast**

`ThreadPoolManager.updatePool` — `pool == null` 时抛 `PoolNotFoundException` 而非静默创建

- [ ] **Step 3: 属性校验**

A. `ThreadPoolProperties.pools` — 添加 `@Valid`
B. `PoolConfig` — 添加 `@AssertTrue` 跨字段校验 `corePoolSize ≤ maximumPoolSize`

- [ ] **Step 4: updateConfig 同步**

`DynamicThreadPoolWrapper.updateConfig` — 添加 `synchronized` + core/max 校验

- [ ] **Step 5: 编译 + 测试**

```bash
mvn -pl client-sdk -am clean test -Dfile.encoding=UTF-8
```

---

#### Task 3.5: 簇 L — 代码清理

**Files:** 10+ 文件

- [ ] **Step 1: 删除死代码**

- `AdminServerAutoConfiguration.configConverter` 字段
- `ConfigAdminService.statsStorage` 字段

- [ ] **Step 2: 命名修正**

- `StatsMapper` → 重命名为 `ThreadPoolStatsMapper`（类名 + 文件名 + 所有引用）
- `ApiKeyPersistenceService.apiKeyConvertor` → `apiKeyConverter`

- [ ] **Step 3: 编译 + 测试**

---

#### Task 3.6: 簇 M — 示例代码修复

**Files:** `DemoService.java` (×2), `DemoController.java`

- [ ] **Step 1: 返回类型修复**

两个 `DemoService` 中 `@AsyncThreadPool` 方法返回类型 `CompletableFuture<String>` → `String`，移除 `CompletableFuture.completedFuture()` 包装。

- [ ] **Step 2: 默认池名**

`DemoController`（sample-cs-client）`defaultValue = "default-pool"` → `defaultValue = "order-pool"`

- [ ] **Step 3: 编译验证**

```bash
mvn -pl samples/sample-local,samples/sample-cs-client -am clean compile -DskipTests
```

---

## 文件清单

- **MODIFY (30+):** 见 File Structure
- **RENAME (1):** `StatsMapper.java` → `ThreadPoolStatsMapper.java`
- **CREATE (0):** 无

## 留待后续

- POM 版本号 `0.0.1-SNAPSHOT` → 留待发布阶段统一处理
- `ThreadPoolAspect` `@annotation` 绑定修复 → 已在本轮完成（`882751f`）
- `CreateThreadPoolAspect` 错误计数 → 已被 `ThreadPoolAspect` 的 `whenComplete` 模式覆盖
- `AsyncExecutorConfig` 四个重复执行器 Bean 提取工厂方法 → 低优先级，留后续重构
- `ThreadPoolMetricsBinder` Gauge 过时引用 → 阶段 3 Micrometer 已处理
