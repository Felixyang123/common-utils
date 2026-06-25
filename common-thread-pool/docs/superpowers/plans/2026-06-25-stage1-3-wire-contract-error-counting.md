# 阶段 1.3 · Wire 契约修复 + 错误计数 · 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: subagent-driven-development.

**Goal:** 修复 client↔server wire 契约 bug(pull URL、删死代码 deleteConfig)+ 用 MockWebServer 锁住客户端契约;修复错误/拒绝计数(error 指标不再恒 0、补 rejectedCount、submitted 正名)。

**Architecture:** Cluster F(wire 契约,含 detector 首批 MockWebServer 测试)+ Cluster G(计数修复)。真实 admin-server 端到端契约测试留作 Plan 1.4。

**Dependency:** Plan 1.2 完成(`dynamic-tp`,`c80b07a`)。

---

## File Structure

```
client-sdk/
├── pom.xml                                          ← MODIFY (加 mockwebserver test dep)
├── src/main/java/com/lezai/threadpool/
│   ├── client/RemoteConfigSourceDetector.java       ← MODIFY (pull URL 单数+version, 删 deleteConfig)
│   ├── core/DynamicThreadPoolWrapper.java           ← MODIFY (rejectedCount, execute()计 submitted, incrementErrorCount)
│   └── aspect/ThreadPoolAspect.java                 ← MODIFY (whenComplete 计 error + 非静默)
└── src/test/java/com/lezai/threadpool/
    ├── RemoteConfigSourceDetectorTest.java          ← CREATE (MockWebServer)
    └── ThreadPoolErrorCountingTest.java             ← CREATE
core/src/main/java/com/lezai/threadpool/bean/
└── ThreadPoolStats.java                             ← MODIFY (加 rejectedTaskCount 字段)
```

---

### Task 1: Cluster F — pull URL 修复 + 删 deleteConfig 死代码 + MockWebServer 测试

**Files:** `pom.xml`, `RemoteConfigSourceDetector.java`, CREATE `RemoteConfigSourceDetectorTest.java`

- [ ] **Step 1: 加 mockwebserver 测试依赖**

在 `client-sdk/pom.xml` 的 `<dependencies>` 内加:
```xml
<dependency>
    <groupId>com.squareup.okhttp3</groupId>
    <artifactId>mockwebserver</artifactId>
    <version>${okhttp.version}</version>
    <scope>test</scope>
</dependency>
```

- [ ] **Step 2: 写 MockWebServer 测试(RED)**

CREATE `client-sdk/src/test/java/com/lezai/threadpool/RemoteConfigSourceDetectorTest.java`:

```java
package com.lezai.threadpool;

import com.lezai.threadpool.client.RemoteConfigSourceDetector;
import com.lezai.threadpool.manager.ThreadPoolManager;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RemoteConfigSourceDetector wire contract")
class RemoteConfigSourceDetectorTest {

    private MockWebServer server;
    private ThreadPoolManager manager;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        manager = new ThreadPoolManager();
    }

    @AfterEach
    void tearDown() throws Exception {
        manager.shutdownNow();
        server.shutdown();
    }

    private RemoteConfigSourceDetector detector(long pullInterval) {
        String baseUrl = server.url("/").toString();
        // strip trailing slash
        baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        return new RemoteConfigSourceDetector(
                baseUrl, "test-app", "test-key",
                30000L, pullInterval, 1000L, 30000L, manager);
    }

    @Test
    @DisplayName("pullConfigs hits the singular /config/{appId}/pull endpoint with version param")
    void pullHitsCorrectEndpoint() throws Exception {
        // empty body -> analyzeResponse returns null, no pools created
        server.enqueue(new MockResponse().setResponseCode(200)
                .setBody("{\"code\":0,\"message\":\"success\",\"data\":null}"));

        RemoteConfigSourceDetector d = detector(0); // 0 = no scheduled polling, single initial pull
        d.start();

        RecordedRequest req = server.takeRequest();
        // CRITICAL: must be singular /config/ matching server OpenThreadPoolConfigController
        assertTrue(req.getPath().startsWith("/open/api/thread-pool/config/test-app/pull"),
                "pull must hit singular /config/.../pull, was: " + req.getPath());
        assertTrue(req.getPath().contains("version="), "pull should send version param");
        assertEquals("test-key", req.getHeader("X-API-Key"));

        d.stop();
    }

    @Test
    @DisplayName("pull parses ThreadPoolAppConfig-shaped server response into pools")
    void pullParsesServerResponse() throws Exception {
        // server returns ThreadPoolAppConfig shape: {appId, configVersion, configs:[...]}
        String body = "{\"code\":0,\"message\":\"success\",\"data\":{"
                + "\"appId\":\"test-app\",\"configVersion\":5,\"configs\":["
                + "{\"poolName\":\"order-pool\",\"corePoolSize\":2,\"maximumPoolSize\":4,"
                + "\"keepAliveTime\":60,\"timeUnit\":\"SECONDS\",\"queueType\":\"BLOCKING_QUEUE\","
                + "\"queueCapacity\":100,\"rejectPolicyType\":\"ABORT\"}]}}";
        server.enqueue(new MockResponse().setResponseCode(200).setBody(body));

        RemoteConfigSourceDetector d = detector(0);
        d.start();
        server.takeRequest();

        // give applyConfigs a moment
        Thread.sleep(200);
        assertNotNull(manager.getPool("order-pool"),
                "pull should create order-pool from server config (DTO contract)");

        d.stop();
    }
}
```

运行(RED — pull URL 还是复数):
```bash
cd /d/code/common-utils/common-thread-pool
mvn -pl client-sdk -am test -Dtest='RemoteConfigSourceDetectorTest' -Dfile.encoding=UTF-8
```
期望: `pullHitsCorrectEndpoint` FAIL(路径是复数 `/configs/`)

- [ ] **Step 3: 修 pull URL — 单数 + version 参数**

打开 `RemoteConfigSourceDetector.java`,`pullConfigs()` 方法(line ~172-173):

当前:
```java
private void pullConfigs() {
    String url = String.format("%s/open/api/thread-pool/configs/%s/pull", serverUrl, appId);
```

改为:
```java
private void pullConfigs() {
    String url = String.format("%s/open/api/thread-pool/config/%s/pull?version=%d",
            serverUrl, appId, configVersion.get());
```

(`configVersion` 是已存在的 `AtomicLong` 字段。)

- [ ] **Step 4: 删除 deleteConfig 死代码**

在 `RemoteConfigSourceDetector.java` 删除整个 `deleteConfig(String appId, String poolName)` 方法(line ~337-359)。它零调用,且服务端 open 控制器无对应 delete 端点(它错误地指向 admin 端点 `/api/...`)。

运行(GREEN):
```bash
mvn -pl client-sdk -am test -Dtest='RemoteConfigSourceDetectorTest' -Dfile.encoding=UTF-8
```
期望: 2 tests PASS

- [ ] **Step 5: Commit**

```bash
cd /d/code/common-utils
git add .
git commit -m "fix(client-sdk): correct pull URL to singular /config/.../pull + version param

- Client pulled /configs/.../pull (plural) but server exposes /config/.../pull (singular) -> 404
- Add version query param so server can short-circuit with not-modified
- Delete dead deleteConfig (zero callers, pointed at non-existent open delete endpoint)
- Add MockWebServer tests asserting exact request path + ThreadPoolAppConfig DTO parsing"
```

---

### Task 2: Cluster G — DynamicThreadPoolWrapper 计数修复

**Files:** `DynamicThreadPoolWrapper.java`, `core/.../ThreadPoolStats.java`, CREATE `ThreadPoolErrorCountingTest.java`

- [ ] **Step 1: ThreadPoolStats 加 rejectedTaskCount 字段**

打开 `core/src/main/java/com/lezai/threadpool/bean/ThreadPoolStats.java`。在已有的 `errorTaskCount` 字段附近加:
```java
/** 被拒绝的任务数 */
private long rejectedTaskCount;
```
(`@Data @Builder` 会自动生成 getter/builder 方法。)

- [ ] **Step 2: 写计数测试(RED)**

CREATE `client-sdk/src/test/java/com/lezai/threadpool/ThreadPoolErrorCountingTest.java`:

```java
package com.lezai.threadpool;

import com.lezai.threadpool.bean.ThreadPoolConfig;
import com.lezai.threadpool.core.DynamicThreadPoolWrapper;
import com.lezai.threadpool.enumeration.RejectPolicyType;
import org.junit.jupiter.api.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DynamicThreadPoolWrapper counting")
class ThreadPoolErrorCountingTest {

    private DynamicThreadPoolWrapper pool;

    @AfterEach
    void tearDown() {
        if (pool != null) pool.shutdownNow();
    }

    @Test
    @DisplayName("execute increments submittedTaskCount (true submission point)")
    void executeCountsSubmitted() throws Exception {
        pool = new DynamicThreadPoolWrapper(ThreadPoolConfig.builder()
                .poolName("submit-pool").corePoolSize(1).maximumPoolSize(1)
                .keepAliveTime(1).timeUnit(TimeUnit.SECONDS).queueCapacity(10).build());

        CountDownLatch latch = new CountDownLatch(3);
        for (int i = 0; i < 3; i++) {
            pool.execute(latch::countDown);
        }
        latch.await(2, TimeUnit.SECONDS);

        assertEquals(3, pool.getSubmittedTaskCount());
    }

    @Test
    @DisplayName("incrementErrorCount increments error counter")
    void incrementErrorCountWorks() {
        pool = new DynamicThreadPoolWrapper(ThreadPoolConfig.builder()
                .poolName("err-pool").corePoolSize(1).maximumPoolSize(1)
                .keepAliveTime(1).timeUnit(TimeUnit.SECONDS).queueCapacity(10).build());

        assertEquals(0, pool.getErrorTaskCount());
        pool.incrementErrorCount();
        pool.incrementErrorCount();
        assertEquals(2, pool.getErrorTaskCount());
    }

    @Test
    @DisplayName("rejected tasks increment rejectedTaskCount")
    void rejectedCounted() throws Exception {
        // core=1, max=1, queue=1, ABORT -> 3rd concurrent task rejected
        pool = new DynamicThreadPoolWrapper(ThreadPoolConfig.builder()
                .poolName("reject-pool").corePoolSize(1).maximumPoolSize(1)
                .keepAliveTime(1).timeUnit(TimeUnit.SECONDS).queueCapacity(1)
                .rejectPolicyType(RejectPolicyType.ABORT).build());

        CountDownLatch block = new CountDownLatch(1);
        pool.execute(() -> {
            try { block.await(2, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
        });
        pool.execute(() -> {}); // queued
        // now core busy + queue full -> next rejected
        int rejected = 0;
        for (int i = 0; i < 3; i++) {
            try { pool.execute(() -> {}); } catch (Exception e) { rejected++; }
        }
        block.countDown();

        assertTrue(pool.getRejectedTaskCount() >= 1, "should have counted rejections");
        assertEquals(rejected, pool.getRejectedTaskCount(), "rejected count matches AbortPolicy throws");
    }

    @Test
    @DisplayName("getStats includes rejectedTaskCount")
    void statsHasRejected() {
        pool = new DynamicThreadPoolWrapper(ThreadPoolConfig.builder()
                .poolName("stats-pool").corePoolSize(1).maximumPoolSize(1)
                .keepAliveTime(1).timeUnit(TimeUnit.SECONDS).queueCapacity(10).build());
        assertEquals(0, pool.getStats().getRejectedTaskCount());
    }
}
```

运行(RED — `incrementErrorCount`/`getRejectedTaskCount` 不存在):
```bash
mvn -pl client-sdk -am test -Dtest='ThreadPoolErrorCountingTest' -Dfile.encoding=UTF-8
```
期望: COMPILE ERROR

- [ ] **Step 3: 改 DynamicThreadPoolWrapper**

打开 `client-sdk/src/main/java/com/lezai/threadpool/core/DynamicThreadPoolWrapper.java`。

A. 加 rejectedTaskCount 字段(在其他 AtomicLong 附近):
```java
private final AtomicLong rejectedTaskCount = new AtomicLong(0);
```

B. 在构造函数体末尾(`log.info(...)` 之前),装饰拒绝处理器以计数:
```java
RejectedExecutionHandler base = getRejectedExecutionHandler();
setRejectedExecutionHandler((r, executor) -> {
    rejectedTaskCount.incrementAndGet();
    base.rejectedExecution(r, executor);
});
```

C. override `execute` 计真实提交,并把 submittedTaskCount 从 beforeExecute 移除:
```java
@Override
public void execute(Runnable command) {
    submittedTaskCount.incrementAndGet();
    super.execute(command);
}
```
并修改 `beforeExecute` — 删除其中的 `submittedTaskCount.incrementAndGet();`(只保留 `super.beforeExecute(t, r);`,以及若需要可不动其它)。

D. 加公开方法:
```java
/** 由异步提交层(如切面)在任务异常完成时调用,累加错误计数。 */
public void incrementErrorCount() {
    errorTaskCount.incrementAndGet();
}

public long getRejectedTaskCount() {
    return rejectedTaskCount.get();
}
```

E. `getStats()` builder 加 `.rejectedTaskCount(getRejectedTaskCount())`。

运行(GREEN):
```bash
mvn -pl client-sdk -am test -Dtest='ThreadPoolErrorCountingTest' -Dfile.encoding=UTF-8
```
期望: 4 tests PASS

- [ ] **Step 4: Commit**

```bash
cd /d/code/common-utils
git add .
git commit -m "feat(client-sdk): real submitted count via execute(), rejectedTaskCount, incrementErrorCount

- Override execute() to count true submissions (was counted in beforeExecute = started)
- Decorate reject handler to count rejectedTaskCount (was never counted)
- Add incrementErrorCount() for async submission layer to record CF failures
- ThreadPoolStats gains rejectedTaskCount field"
```

---

### Task 3: Cluster G — ThreadPoolAspect 错误可见性

**Files:** `ThreadPoolAspect.java`

- [ ] **Step 1: 改切面 — whenComplete 计 error + 非静默**

打开 `client-sdk/src/main/java/com/lezai/threadpool/aspect/ThreadPoolAspect.java`。

将 `around` 方法的 future 构造及 return 部分(line ~49-61)替换为:
```java
CompletableFuture<Object> future = CompletableFuture.supplyAsync(() -> {
    try {
        return joinPoint.proceed();
    } catch (Throwable e) {
        throw new java.util.concurrent.CompletionException(e);
    }
}, pool);

// 错误可见性:在 CF 完成层捕获异常(afterExecute 的 throwable 在异步路径恒为 null),
// 计数并记日志 —— 即便是 fire-and-forget 的非 Future 方法,异常也不再被静默吞没。
CompletableFuture<Object> tracked = future.whenComplete((result, ex) -> {
    if (ex != null) {
        pool.incrementErrorCount();
        log.error("Async task {} failed in pool {}", getMethodName(method), poolName, ex);
    }
});

if (Future.class.isAssignableFrom(method.getReturnType())) {
    return tracked;
}
return null;
```

注意 `method` 变量需在 future 构造前已声明(当前代码 `Method method = getMethod(joinPoint);` 在 future 之前,确认顺序;若在之后则上移)。

- [ ] **Step 2: 编译 + 全量回归**

```bash
cd /d/code/common-utils/common-thread-pool
mvn -pl client-sdk -am test -Dfile.encoding=UTF-8
```
期望: 全绿(41 + 2 detector + 4 counting = 47)

- [ ] **Step 3: Commit**

```bash
cd /d/code/common-utils
git add .
git commit -m "fix(client-sdk): aspect records async task failures via whenComplete (no silent swallow)

- afterExecute throwable is null for supplyAsync path -> error count was dead
- Attach whenComplete to count errors + log exception, including for fire-and-forget
  (non-Future return) @AsyncThreadPool methods that previously discarded the future"
```

---

### Task 4: 全量回归

- [ ] **Step 1: client-sdk 全量 + admin-server 编译**

```bash
cd /d/code/common-utils/common-thread-pool
mvn -pl client-sdk -am clean test -Dfile.encoding=UTF-8
mvn -pl admin-server -am clean compile -DskipTests
```
期望: 47 tests green; admin-server BUILD SUCCESS(ThreadPoolStats 加字段向后兼容)

- [ ] **Step 2: 确认 ThreadPoolStats 字段未破坏 admin-server 反序列化**

admin-server 接收 stats report 反序列化 ThreadPoolStats。新增 `rejectedTaskCount` 是新字段,旧数据无此字段时默认 0,兼容。运行 admin-server stats 相关测试确认:
```bash
mvn -pl admin-server test -Dtest='*Stats*' -Dfile.encoding=UTF-8
```

---

## 文件清单
- MODIFY: `client-sdk/pom.xml`, `RemoteConfigSourceDetector.java`, `DynamicThreadPoolWrapper.java`, `ThreadPoolAspect.java`, `core/.../ThreadPoolStats.java`
- CREATE: `RemoteConfigSourceDetectorTest.java`, `ThreadPoolErrorCountingTest.java`

## 留待 Plan 1.4
- 真实 admin-server 端到端契约测试(同进程拉起 admin-server + 真实 detector 走 HTTP,锁住完整 register→pull→subscribe→stats 往返)。
