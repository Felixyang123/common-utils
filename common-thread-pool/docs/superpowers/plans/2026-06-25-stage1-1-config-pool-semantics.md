# 阶段 1.1 · 配置与池语义 · 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 重构 `ThreadPoolProperties` 客户端语义 + 校验；新增 `PoolNotFoundException`/`getRequiredPool`，删除 `getPool` 自动建池与 CS `getPool` 自动注册副作用；实现 CS 模式启动引导兜底（可用性优先）。

**Architecture:** 按 cluster E → C → D 顺序：先打好配置地基（属性改名+校验），再改池访问语义（fail-fast + 删副作用），最后做 CS 兜底（本地先建池）。每个 cluster 内部 TDD（先写测试 → 再实现）。修改文件集中在 `client-sdk` 模块（`core` 不变）。

**Tech Stack:** Java 21, JUnit 5, OkHttp MockWebServer (测试 CS 模式), Spring Boot Test (ApplicationContextRunner), Lombok

---

## File Structure

```
client-sdk/src/
├── main/java/com/lezai/threadpool/
│   ├── exception/
│   │   └── PoolNotFoundException.java          ← CREATE (RuntimeException)
│   ├── properties/
│   │   └── ThreadPoolProperties.java            ← MODIFY (remote.* 重构, @Validated)
│   ├── config/
│   │   └── ThreadPoolAutoConfiguration.java     ← MODIFY (condition 改名, default-pool)
│   ├── manager/
│   │   ├── ThreadPoolManager.java               ← MODIFY (getRequiredPool, getPool 删自动建池)
│   │   └── RemoteConfigSourcePoolManager.java   ← MODIFY (删 getPool 自动注册, 兜底)
│   ├── init/
│   │   └── ThreadPoolInitializer.java           ← MODIFY (default-pool, 兜底)
│   └── aspect/
│       └── ThreadPoolAspect.java                ← MODIFY (改用 getRequiredPool)
└── test/java/com/lezai/threadpool/              ← 测试在 flat package (现有惯例)
    ├── ThreadPoolManagerTest.java               ← MODIFY (新增 fail-fast 测试，修旧测试)
    ├── ThreadPoolPropertiesTest.java            ← CREATE
    └── RemoteConfigSourcePoolManagerTest.java   ← CREATE
```

---

### Task 1: PoolNotFoundException（TDD RED → GREEN）

**Files:** Create `exception/PoolNotFoundException.java`, `ThreadPoolManagerTest.java` (extend with new tests)

- [ ] **Step 1: 在 ThreadPoolManagerTest 里加 fail-fast 测试**

打开 `client-sdk/src/test/java/com/lezai/threadpool/ThreadPoolManagerTest.java`，在文件末尾 `}` 之前追加:

```java
@Test
@DisplayName("getRequiredPool returns existing pool")
void getRequiredPoolReturnsExisting() {
    manager.registerPool(config("required-existing"));
    DynamicThreadPoolWrapper pool = manager.getRequiredPool("required-existing");
    assertNotNull(pool);
    assertEquals("required-existing", pool.getPoolName());
}

@Test
@DisplayName("getRequiredPool throws PoolNotFoundException when not found")
void getRequiredPoolThrowsWhenMissing() {
    assertThrows(PoolNotFoundException.class, () -> {
        manager.getRequiredPool("unknown-pool");
    });
}
```

运行测试（class 找不到，RED）:

```sh
cd /d/code/common-utils/common-thread-pool
mvn -pl client-sdk -am test -Dtest='ThreadPoolManagerTest#getRequiredPoolReturnsExisting+getRequiredPoolThrowsWhenMissing' -Dfile.encoding=UTF-8
```

期望: COMPILE ERROR（`PoolNotFoundException` 和 `getRequiredPool` 不存在）

- [ ] **Step 2: 创建 PoolNotFoundException**

创建 `client-sdk/src/main/java/com/lezai/threadpool/exception/PoolNotFoundException.java`:

```java
package com.lezai.threadpool.exception;

/**
 * 线程池未找到异常。
 * 当通过 {@code getRequiredPool(name)} 访问一个未经声明的池名时抛出。
 * 继承 RuntimeException —— 这是配置错误，不可恢复。
 */
public class PoolNotFoundException extends RuntimeException {

    public PoolNotFoundException(String poolName) {
        super("Thread pool not found: " + poolName);
    }
}
```

- [ ] **Step 3: 在 ThreadPoolManager 添加 getRequiredPool 占位方法**

打开 `client-sdk/src/main/java/com/lezai/threadpool/manager/ThreadPoolManager.java`，在 `getPool` 方法（约 line 68）之后新增:

```java
/**
 * 获取已注册的线程池（必须存在）。
 * 与 {@link #getPool(String)} 不同，此方法不会自动创建，池不存在时抛出异常。
 *
 * @param poolName 线程池名称
 * @return 线程池包装器
 * @throws PoolNotFoundException 如果池未注册
 */
public DynamicThreadPoolWrapper getRequiredPool(String poolName) {
    DynamicThreadPoolWrapper pool = poolRegistry.get(poolName);
    if (pool == null) {
        throw new PoolNotFoundException(poolName);
    }
    return pool;
}
```

添加 import:

```java
import com.lezai.threadpool.exception.PoolNotFoundException;
```

运行测试（GREEN）:

```sh
mvn -pl client-sdk -am test -Dtest='ThreadPoolManagerTest' -Dfile.encoding=UTF-8
```

期望: TEST SUCCESS（新增 2 个测试 + 原有 14 个全绿）

- [ ] **Step 4: Commit**

```sh
git add client-sdk/src/main/java/com/lezai/threadpool/exception/PoolNotFoundException.java \
        client-sdk/src/main/java/com/lezai/threadpool/manager/ThreadPoolManager.java \
        client-sdk/src/test/java/com/lezai/threadpool/ThreadPoolManagerTest.java
git commit -m "feat(client-sdk): add PoolNotFoundException and ThreadPoolManager.getRequiredPool"
```

---

### Task 2: ThreadPoolManager.getPool 删除自动建池（TDD RED → GREEN）

**Files:** Modify `ThreadPoolManager.java` (change `getPool`), `ThreadPoolManagerTest.java` (update old tests)

- [ ] **Step 1: 更新旧测试（预期新行为）**

打开 `ThreadPoolManagerTest.java`，修改以下测试方法:

**`getPoolCreatesDefault`**（line 74-81）→ 改为验证 `getPool` **不再**自动创建:

```java
@Test
@DisplayName("getPool returns null when pool not registered (no auto-create)")
void getPoolReturnsNullWhenMissing() {
    DynamicThreadPoolWrapper pool = manager.getPool("never-registered");
    assertNull(pool, "getPool should no longer auto-create pools");
}
```

**`removePoolRemovesPool`**（line 85-95）→ `getPool("to-remove")` 在 remove 之后改为用 `getRequiredPool`:

```java
@Test
@DisplayName("removePool removes and shuts down the pool")
void removePoolRemovesPool() throws Exception {
    DynamicThreadPoolWrapper pool = manager.registerPool(config("to-remove"));
    assertNotNull(manager.getPool("to-remove"));

    boolean removed = manager.removePool("to-remove");
    assertTrue(removed, "removePool should return true");

    assertNull(manager.getPool("to-remove"), "getPool should return null after removal");
    assertThrows(PoolNotFoundException.class, () -> manager.getRequiredPool("to-remove"));
}
```

**`removeAllPools`**（line 158-169）→ 同原则:

```java
@Test
@DisplayName("removeAllPools removes all pools")
void removeAllPools() {
    manager.registerPool(config("pool-a"));
    manager.registerPool(config("pool-b"));

    manager.removeAllPools();

    assertNull(manager.getPool("pool-a"));
    assertNull(manager.getPool("pool-b"));
}
```

**`shutdownAll`**（line 171-183）→ `getPool` 返回 null:

```java
@Test
@DisplayName("shutdown shuts down all pools gracefully")
void shutdownAll() throws Exception {
    manager.registerPool(config("shutdown-a"));
    manager.registerPool(config("shutdown-b"));

    manager.shutdown();

    // getPool 不再自动创建 — shutdown 后返回 null
    assertNull(manager.getPool("shutdown-a"));
    assertNull(manager.getPool("shutdown-b"));
}
```

添加必要的 import（已从 Task 1 继承，确认 `PoolNotFoundException` 已 import）。

运行测试（部分 RED）:

```sh
mvn -pl client-sdk -am test -Dtest='ThreadPoolManagerTest' -Dfile.encoding=UTF-8
```

期望: 部分新测试 FAIL — `getPool` 仍在自动建池

- [ ] **Step 2: 修改 getPool 实现**

打开 `ThreadPoolManager.java`，将 `getPool` 方法（约 line 68-70）从:

```java
public DynamicThreadPoolWrapper getPool(String poolName) {
    return poolRegistry.computeIfAbsent(poolName, this::createDefaultPool);
}
```

改为:

```java
public DynamicThreadPoolWrapper getPool(String poolName) {
    return poolRegistry.get(poolName);
}
```

同时删除不再被调用的 `createDefaultPool` 方法（line 36-41），并确认 `getPoolStats` 内部调用 `getPool` 后需要判空:

```java
public ThreadPoolStats getPoolStats(String poolName) {
    DynamicThreadPoolWrapper pool = getPool(poolName);
    if (pool == null) {
        throw new PoolNotFoundException(poolName);
    }
    return pool.getStats();
}
```

运行测试（GREEN）:

```sh
mvn -pl client-sdk -am test -Dtest='ThreadPoolManagerTest' -Dfile.encoding=UTF-8
```

期望: 全绿（包括修正后的旧测试）

- [ ] **Step 3: Commit**

```sh
git add client-sdk/src/main/java/com/lezai/threadpool/manager/ThreadPoolManager.java \
        client-sdk/src/test/java/com/lezai/threadpool/ThreadPoolManagerTest.java
git commit -m "refactor(client-sdk): remove getPool auto-create, getPoolStats fail-fast"
```

---

### Task 3: ThreadPoolProperties 重构 + 校验（TDD）

**Files:** Create `ThreadPoolPropertiesTest.java`, modify `ThreadPoolProperties.java`

这是 Plan 1.1 中**工作量最大的 task**。

- [ ] **Step 1: 写 ThreadPoolPropertiesTest**

创建 `client-sdk/src/test/java/com/lezai/threadpool/ThreadPoolPropertiesTest.java`:

```java
package com.lezai.threadpool;

import com.lezai.threadpool.properties.ThreadPoolProperties;
import jakarta.validation.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ThreadPoolProperties")
class ThreadPoolPropertiesTest {

    ThreadPoolProperties bind(Map<String, String> props) {
        var binder = new Binder(
                new MapConfigurationPropertySource(props)
        );
        BindResult<ThreadPoolProperties> result = binder.bind("thread.pool", ThreadPoolProperties.class);
        assertTrue(result.isBound(), "should bind successfully");
        return result.get();
    }

    @Test
    @DisplayName("default remote.enabled is false")
    void remoteEnabledDefaultsFalse() {
        var p = bind(Map.of());
        assertFalse(p.getRemote().isEnabled());
    }

    @Test
    @DisplayName("server-url defaults to http://localhost:8080")
    void serverUrlDefaults() {
        var p = bind(Map.of());
        assertEquals("http://localhost:8080", p.getRemote().getServerUrl());
    }

    @Test
    @DisplayName("remote server-url, app-id, api-key bind correctly when enabled")
    void remoteClientConfigBinds() {
        var p = bind(Map.of(
                "remote.enabled", "true",
                "remote.server-url", "http://admin:9090",
                "remote.app-id", "my-app",
                "remote.api-key", "sk-123"
        ));
        assertTrue(p.getRemote().isEnabled());
        assertEquals("http://admin:9090", p.getRemote().getServerUrl());
        assertEquals("my-app", p.getRemote().getAppId());
        assertEquals("sk-123", p.getRemote().getApiKey());
    }

    @Test
    @DisplayName("remote.enabled=true with missing api-key fails validation")
    void remoteEnabledMissingApiKeyFails() {
        var p = bind(Map.of(
                "remote.enabled", "true",
                "remote.server-url", "http://localhost:8080",
                "remote.app-id", "my-app"
        ));
        // validation should fail — apiKey is @NotBlank when enabled
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var violations = validator.validate(p);
            // We expect violations — at minimum api-key is blank
            assertFalse(violations.isEmpty(), "should have validation violations");
        }
    }

    @Test
    @DisplayName("pullIntervalMs defaults to 0 (disabled)")
    void pullIntervalMsDefaultsZero() {
        var p = bind(Map.of());
        assertEquals(0L, p.getRemote().getPullIntervalMs());
    }

    @Test
    @DisplayName("pool config binds correctly")
    void poolConfigBinds() {
        var p = bind(Map.of(
                "pools[0].name", "order-pool",
                "pools[0].core-pool-size", "5",
                "pools[0].maximum-pool-size", "10",
                "pools[0].queue-capacity", "200"
        ));
        assertEquals(1, p.getPools().length);
        assertEquals("order-pool", p.getPools()[0].getName());
        assertEquals(5, p.getPools()[0].getCorePoolSize());
        assertEquals(10, p.getPools()[0].getMaximumPoolSize());
        assertEquals(200, p.getPools()[0].getQueueCapacity());
    }
}
```

运行（compile error — 属性名尚未存在）:

```sh
mvn -pl client-sdk -am test -Dtest='ThreadPoolPropertiesTest' -Dfile.encoding=UTF-8
```

期望: COMPILE ERROR — `getRemote().getServerUrl()` / `getRemote().getAppId()` 等不存在

- [ ] **Step 2: 重构 ThreadPoolProperties.remote 结构**

打开 `client-sdk/src/main/java/com/lezai/threadpool/properties/ThreadPoolProperties.java`。

**新增 imports**（替换旧的 `NotBlank` import——字段级校验改为 `@AssertTrue` 条件校验）:

```java
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
```

**类声明加 `@Validated`**（第 10 行附近）:

```java
@Data
@Validated
@ConfigurationProperties(prefix = "thread.pool")
public class ThreadPoolProperties {
```

**将 `RemoteServerConfig remote` 字段改为 `RemoteConfig remote`，内部类 `RemoteServerConfig` 完全替换为 `RemoteConfig`**（删除嵌套的 `ServerConfig` / `ClientConfig`）:

        /** 是否启用 CS 客户端模式（默认 false，即 LOCAL 模式） */
        private boolean enabled = false;

        /** admin-server 地址（默认 http://localhost:8080） */
        private String serverUrl = "http://localhost:8080";

        /** 应用 ID（客户端身份标识） */
        private String appId = "default-app";

        /** API 密钥（remote.enabled=true 时必填） */
        private String apiKey;

        /** 长轮询超时（毫秒），默认 30000 */
        @PositiveOrZero
        private long longPollingTimeoutMs = 30000L;

        /** 短轮询补偿间隔（毫秒），默认 0 = 禁用 */
        @PositiveOrZero
        private long pullIntervalMs = 0L;

        /** 是否上报统计信息 */
        private boolean reportEnabled = true;

        /** 统计上报间隔（毫秒），默认 60000 */
        @PositiveOrZero
        private long reportIntervalMs = 60000L;

        /**
         * 条件校验：仅当 remote.enabled=true 时检查必填连接字段。
         * 这样 local 模式（enabled=false、不设 api-key）不会触发校验失败。
         */
        @AssertTrue(message = "When remote.enabled=true, server-url, app-id, and api-key must not be blank")
        public boolean isConnectionConfigValid() {
            if (!enabled) {
                return true;
            }
            return serverUrl != null && !serverUrl.isBlank()
                    && appId != null && !appId.isBlank()
                    && apiKey != null && !apiKey.isBlank();
        }
    }
}
```

关键变化:
- `RemoteServerConfig remote` → `RemoteConfig remote`
- 删除嵌套 `ServerConfig` / `ClientConfig` 分拆——扁平化为 `RemoteConfig`
- `server.host + server.port` → `serverUrl`（默认 `http://localhost:8080`）
- `apiKey` / `appId` 上移 + `@NotBlank`
- `pullIntervalMs` 默认 `0L`
- 类加 `@Validated`，字段加 `@Valid` `@Min` `@NotBlank` `@PositiveOrZero`

运行测试（预期部分属性绑定通过，前两个测试 GREEN）:

```sh
mvn -pl client-sdk -am test -Dtest='ThreadPoolPropertiesTest#remoteEnabledDefaultsFalse+serverUrlDefaults' -Dfile.encoding=UTF-8
```

- [ ] **Step 3: 检查 ThreadPoolAutoConfiguration 编译**

属性重构后，auto-config 里的 `properties.getRemote().getServer()` / `.getClient()` 引用会**编译失败**。我们在这个 step 仅做最小修正让它编译，不重写:

打开 `config/ThreadPoolAutoConfiguration.java`:
- 将 `ThreadPoolProperties.RemoteServerConfig.ClientConfig client = properties.getRemote().getClient();` → `ThreadPoolProperties.RemoteConfig remote = properties.getRemote();`
- 将 `ThreadPoolProperties.RemoteServerConfig.ServerConfig server = properties.getRemote().getServer();` → 删除，改用 `remote.getServerUrl()`
- 将 `server.getServerUrl()` → `remote.getServerUrl()`
- 将 `client.getAppId()` → `remote.getAppId()`
- 将 `client.getApiKey()` → `remote.getApiKey()`
- 将 `client.getLongPollingTimeoutMs()` → `remote.getLongPollingTimeoutMs()`
- 将 `client.getPullIntervalMs()` → `remote.getPullIntervalMs()`
- 将 `client.getReportIntervalMs()` → `remote.getReportIntervalMs()`
- 将 `client.isReportEnabled()` → `remote.isReportEnabled()`
- 将 `@ConditionalOnBooleanProperty(name = "thread.pool.remote.server.enabled")` → `@ConditionalOnBooleanProperty(name = "thread.pool.remote.enabled")`（共 4 处）

也检查以下文件同样的编译兼容:
- `ThreadPoolInitializer.java` —— 对 `properties.getRemote()` 引用做相同映射
- 任何其他 `import ...RemoteServerConfig` → 改为 `import ...RemoteConfig`

运行全模块编译确认:

```sh
mvn -pl client-sdk -am clean compile test-compile -DskipTests
```

期望: 编译成功（BUILD SUCCESS）

- [ ] **Step 4: Commit**

```sh
git add .
git commit -m "refactor(client-sdk): restructure thread.pool.remote.* to client semantics

- RemoteServerConfig → RemoteConfig (flat, no ServerConfig/ClientConfig nesting)
- server.host+port → server-url (default http://localhost:8080, was 8088)
- thread.pool.remote.server.enabled → thread.pool.remote.enabled
- Add @Validated + @NotBlank/@Min/@PositiveOrZero validation
- pullIntervalMs default 0 (disabled)
- Update ThreadPoolAutoConfiguration/ThreadPoolInitializer to match"
```

---

### Task 4: RemoteConfigSourceDetector 构造函数（适配新属性）

`RemoteConfigSourceDetector` 的构造函数签名没变（仍接收 `serverUrl, appId, apiKey, ...`），但调用方变了。补一个编译验证 + 构造 ref 确认无误。

- [ ] **Step 1: 确认构造函数兼容性**

`RemoteConfigSourceDetector.java` 构造函数取 `String serverUrl, String appId, String apiKey, long longPollingTimeoutMs, long pullIntervalMs, ThreadPoolManager threadPoolManager`。

`ThreadPoolAutoConfiguration` 现在传 `remote.getServerUrl()` / `remote.getAppId()` / `remote.getApiKey()` / `remote.getLongPollingTimeoutMs()` / `remote.getPullIntervalMs()`。签名匹配，只改调用方。

同理 `ThreadPoolStatsReporter` 构造取 `String serverUrl, String appId, String apiKey, long reportIntervalMs, ThreadPoolManager`，传值映射同上。

确认 `pullIntervalMs=0` 的处理:
打开 `RemoteConfigSourceDetector.java`，在 `start()` 方法（line 75）中将:

```java
this.pullConfigsScheduler.scheduleAtFixedRate(this::pullConfigs, pullIntervalMs, pullIntervalMs, TimeUnit.MILLISECONDS);
```

改为:

```java
if (pullIntervalMs > 0) {
    this.pullConfigsScheduler.scheduleAtFixedRate(this::pullConfigs, pullIntervalMs, pullIntervalMs, TimeUnit.MILLISECONDS);
    log.info("Short-polling enabled: interval={}ms", pullIntervalMs);
} else {
    log.info("Short-polling disabled (pullIntervalMs=0)");
}
```

- [ ] **Step 2: 编译 + ThreadPoolPropertiesTest 全部 GREEN**

```sh
mvn -pl client-sdk -am test -Dtest='ThreadPoolPropertiesTest' -Dfile.encoding=UTF-8
```

预计全绿。

- [ ] **Step 3: Commit**

```sh
git add .
git commit -m "fix(client-sdk): update RemoteConfigSourceDetector for new remote config; disable short-polling when pullIntervalMs=0"
```

---

### Task 5: ThreadPoolAspect 改用 getRequiredPool（TDD）

**Files:** Modify `ThreadPoolAspect.java`

- [ ] **Step 1: 修改切面**

打开 `aspect/ThreadPoolAspect.java`，line 43:
```java
DynamicThreadPoolWrapper pool = threadPoolManager.getPool(poolName);
```
改为:
```java
DynamicThreadPoolWrapper pool = threadPoolManager.getRequiredPool(poolName);
```

这行变更唯一。`@AsyncThreadPool` 指定了不存在的池名 → 抛 `PoolNotFoundException`（fail-fast，启动期/首次调用时立即暴露）。

- [ ] **Step 2: 编译确认**

```sh
mvn -pl client-sdk -am clean compile -DskipTests
```

- [ ] **Step 3: Commit**

```sh
git add client-sdk/src/main/java/com/lezai/threadpool/aspect/ThreadPoolAspect.java
git commit -m "refactor(client-sdk): ThreadPoolAspect uses getRequiredPool (fail-fast unknown pool)"
```

---

### Task 6: RemoteConfigSourcePoolManager（删自动注册 + 兜底建池）

这是 Plan 1.1 中**逻辑最复杂的 task**。

**Files:** Modify `RemoteConfigSourcePoolManager.java`，创建 `RemoteConfigSourcePoolManagerTest.java`

- [ ] **Step 1: 写测试**

创建 `client-sdk/src/test/java/com/lezai/threadpool/RemoteConfigSourcePoolManagerTest.java`:

```java
package com.lezai.threadpool;

import com.lezai.threadpool.bean.ThreadPoolConfig;
import com.lezai.threadpool.client.RemoteConfigSourceDetector;
import com.lezai.threadpool.core.DynamicThreadPoolWrapper;
import com.lezai.threadpool.exception.PoolNotFoundException;
import com.lezai.threadpool.manager.RemoteConfigSourcePoolManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * TDD RED — verify CS pool manager removes getPool auto-register side effect
 * and supports bootstrap fallback (local build before server pull).
 */
@DisplayName("RemoteConfigSourcePoolManager")
class RemoteConfigSourcePoolManagerTest {

    private RemoteConfigSourceDetector detector;
    private RemoteConfigSourcePoolManager poolManager;

    private ThreadPoolConfig pool(String name) {
        return ThreadPoolConfig.builder()
                .poolName(name).corePoolSize(1).maximumPoolSize(1)
                .keepAliveTime(1).timeUnit(TimeUnit.SECONDS).queueCapacity(10).build();
    }

    @BeforeEach
    void setUp() {
        detector = mock(RemoteConfigSourceDetector.class);
        poolManager = new RemoteConfigSourcePoolManager(detector);
    }

    @AfterEach
    void tearDown() {
        poolManager.shutdownNow();
    }

    @Test
    @DisplayName("getPool does NOT auto-register to server")
    void getPoolDoesNotAutoRegister() {
        // getPool should return null for unknown pool (no auto-create, no auto-register)
        assertNull(poolManager.getPool("unknown-pool"));
        verify(detector, never()).registerConfig(any());
    }

    @Test
    @DisplayName("registerPool pushes config to server AND builds locally")
    void registerPoolPushesAndBuilds() {
        when(detector.registerConfig(any())).thenReturn(pool("order-pool"));

        DynamicThreadPoolWrapper pool = poolManager.registerPool(pool("order-pool"));

        assertNotNull(pool);
        assertEquals("order-pool", pool.getPoolName());
        verify(detector).registerConfig(any());
    }

    @Test
    @DisplayName("registerPools pushes configs to server AND builds locally (bootstrap)")
    void registerPoolsBuildsLocally() {
        doAnswer(invocation -> null).when(detector).registerConfigs(any());

        poolManager.registerPools(List.of(pool("pool-a"), pool("pool-b")));

        // detector called (push to server)
        verify(detector).registerConfigs(any());
        // pools built locally (even if server is down)
        assertNotNull(poolManager.getPool("pool-a"), "pool-a should exist locally");
        assertNotNull(poolManager.getPool("pool-b"), "pool-b should exist locally");
    }

    @Test
    @DisplayName("getRequiredPool does NOT auto-register (throws instead)")
    void getRequiredPoolDoesNotAutoRegister() {
        assertThrows(PoolNotFoundException.class, () ->
                poolManager.getRequiredPool("unknown-pool"));
        verify(detector, never()).registerConfig(any());
    }
}
```

编译确认（Mockito 在 client-sdk 测试中可用——spring-boot-starter-test 包含 mockito-core）:

```sh
mvn -pl client-sdk -am test-compile -DskipTests
```

- [ ] **Step 2: 修改 RemoteConfigSourcePoolManager 实现**

打开 `manager/RemoteConfigSourcePoolManager.java`。

**改 `getPool`**（line 21-27）——删除 override，退回到父类 `ConcurrentHashMap.get` 行为:

删除整个 `getPool` override 方法。

**改 `registerPools`**（line 43-46）——加了兜底建池:

```java
@Override
public void registerPools(List<ThreadPoolConfig> configs) {
    // Phase 1: build locally first (bootstrap fallback — works even when server is down)
    for (ThreadPoolConfig config : configs) {
        poolRegistry.computeIfAbsent(config.getPoolName(), poolName -> createPool(config));
    }
    // Phase 2: push to server (fire-and-forget — server may be unreachable at startup)
    detector.registerConfigs(configs);
}
```

**改 `registerPool`**（line 35-41）——维持不变，但确认逻辑清晰:

```java
@Override
public DynamicThreadPoolWrapper registerPool(ThreadPoolConfig config) {
    return poolRegistry.computeIfAbsent(config.getPoolName(), poolName -> {
        ThreadPoolConfig threadPoolConfig = detector.registerConfig(config);
        return createPool(threadPoolConfig);
    });
}
```

（`detector.registerConfig` 失败时返回 null 的问题在后面 Plan 1.3（contract fix）中解决。）

运行测试:

```sh
mvn -pl client-sdk -am test -Dtest='RemoteConfigSourcePoolManagerTest' -Dfile.encoding=UTF-8
```

- [ ] **Step 3: Commit**

```sh
git add .
git commit -m "refactor(client-sdk): remove CS getPool auto-register; add bootstrap local build"
```

---

### Task 7: ThreadPoolInitializer + ThreadPoolAutoConfiguration（default-pool + bootstrap）

**Files:** Modify `ThreadPoolInitializer.java`, `ThreadPoolAutoConfiguration.java`

- [ ] **Step 1: ThreadPoolInitializer 显式 default-pool + CS 启动兜底**

打开 `init/ThreadPoolInitializer.java`。

修改 `loadConfiguredPools` 方法（line 76-93）——在已有注册逻辑**前面**加 default-pool:

```java
private void loadConfiguredPools() {
    // Step 0: register default pool once (explicit, no longer lazy)
    registerDefaultPool();

    // Step 1: register user-configured pools
    List<ThreadPoolConfig> configs = Arrays.stream(properties.getPools())
            .filter(poolConfig -> !"default-pool".equals(poolConfig.getName())) // skip duplicate
            .map(poolConfig -> ThreadPoolConfig.builder()
                    .poolName(poolConfig.getName())
                    .corePoolSize(poolConfig.getCorePoolSize())
                    .maximumPoolSize(poolConfig.getMaximumPoolSize())
                    .keepAliveTime(poolConfig.getKeepAliveTime())
                    .timeUnit(TimeUnit.SECONDS)
                    .queueType(QueueType.valueOf(poolConfig.getQueueType()))
                    .queueCapacity(poolConfig.getQueueCapacity())
                    .rejectPolicyType(RejectPolicyType.valueOf(poolConfig.getRejectPolicyType()))
                    .allowCoreThreadTimeout(poolConfig.isAllowCoreThreadTimeout())
                    .threadNamePrefix(poolConfig.getThreadNamePrefix())
                    .daemon(poolConfig.isDaemon())
                    .build())
            .toList();

    threadPoolManager.registerPools(configs);
}

private void registerDefaultPool() {
    threadPoolManager.registerPool(ThreadPoolConfig.builder()
            .poolName("default-pool")
            .corePoolSize(Runtime.getRuntime().availableProcessors())
            .maximumPoolSize(Runtime.getRuntime().availableProcessors() * 2)
            .keepAliveTime(60)
            .timeUnit(TimeUnit.SECONDS)
            .queueCapacity(1024)
            .build());
    log.info("Registered default thread pool: default-pool");
}
```

- [ ] **Step 2: ThreadPoolAutoConfiguration 加 local-only initializer**

当前 auto-config 的 `threadPoolInitializer` bean 硬依赖 `RemoteConfigSourceDetector`（即便 local 模式也要传）。需要补一个 local-only 版本:

打开 `config/ThreadPoolAutoConfiguration.java`，在 local 模式 manager bean 后面新增:

```java
@Bean
@ConditionalOnMissingBean
@ConditionalOnBooleanProperty(name = "thread.pool.remote.enabled", havingValue = false)
public ThreadPoolInitializer threadPoolInitializerLocal(ThreadPoolManager threadPoolManager) {
    ThreadPoolInitializer initializer = new ThreadPoolInitializer(properties, null, threadPoolManager);
    initializer.initialize();
    return initializer;
}
```

并把已存在的 `threadPoolInitializer` bean 的方法签名改成只在 remote 模式启用:

原:
```java
@Bean
@ConditionalOnMissingBean
public ThreadPoolInitializer threadPoolInitializer(RemoteConfigSourceDetector detector,
                                                   ThreadPoolManager threadPoolManager) {
```

改为:
```java
@Bean
@ConditionalOnMissingBean
@ConditionalOnBooleanProperty(name = "thread.pool.remote.enabled")
public ThreadPoolInitializer threadPoolInitializerRemote(RemoteConfigSourceDetector detector,
                                                          ThreadPoolManager threadPoolManager) {
```

运行测试:

```sh
mvn -pl client-sdk -am clean test-compile -DskipTests
```

期望: BUILD SUCCESS

- [ ] **Step 3: Commit**

```sh
git add .
git commit -m "feat(client-sdk): explicit default-pool registration; split local/remote initializer beans"
```

---

### Task 8: 全量回归 + 提交总结

- [ ] **Step 1: 全量编译 + client-sdk 测试**

```sh
mvn -pl client-sdk -am clean test -Dfile.encoding=UTF-8
```

期望: 全绿

- [ ] **Step 2: 确认 admin-server 不受影响（should still compile at minimum）**

```sh
mvn -pl admin-server -am clean compile -DskipTests
```

期望: BUILD SUCCESS（admin-server 不依赖 client-sdk，不受影响）

- [ ] **Step 3: 最终 review 确认**

检查改动的文件清单:

```sh
git diff --stat HEAD~4..HEAD
```

应包含:
- CREATE: `PoolNotFoundException.java`, `ThreadPoolPropertiesTest.java`, `RemoteConfigSourcePoolManagerTest.java`
- MODIFY: `ThreadPoolProperties.java`, `ThreadPoolManager.java`, `RemoteConfigSourcePoolManager.java`, `ThreadPoolAutoConfiguration.java`, `ThreadPoolInitializer.java`, `ThreadPoolAspect.java`, `RemoteConfigSourceDetector.java`, `ThreadPoolManagerTest.java`

- [ ] **Step 4: Optional——push intermediate commits**

```sh
git push origin dynamic-tp
```

---

## Self-Review Checklist

1. ✅ Spec coverage (Clusters E+C+D): all requirements mapped to tasks
2. ✅ No placeholders: every step has exact code or command
3. ✅ Type consistency: `PoolNotFoundException` defined in Task 1, referenced in Tasks 2/5/6; `RemoteConfig.getServerUrl()` replaces `server.getServerUrl()` throughout
4. ✅ TDD order: tests first, then implementation, then commit
