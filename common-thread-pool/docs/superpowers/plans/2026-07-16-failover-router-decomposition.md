# FailoverRouter Decomposition Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Decompose the God Class `FailoverRouter` into independently testable, replaceable components — `RoutingStrategy` (Strategy), `HealthChecker` (Observer push), `ServerNode` embeds `CircuitBreaker` — composed by a thin orchestration `FailoverRouter`.

**Architecture:** Three-layer decoupling. Layer 1: health + breaker state pushed into `NodeManager` which maintains a cached candidate list (no runtime filtering). Layer 2: routing selection via pluggable `RoutingStrategy`. Layer 3: `FailoverRouter` as pure orchestration: `select → node.execute → retry`. Components interact only through callback interfaces, registered externally in the composition root. Circuit-breaker cooldown uses a global single-thread `CircuitBreakerScheduler`, removing the deadlock where OPEN nodes could never recover.

**Tech Stack:** Java 21, Spring Boot 3.x, OkHttp + MockWebServer, JUnit 5, AssertJ, Mockito, Maven (JDK 21 @ `C:\Users\wangyang\.jdks\ms-21.0.8`).

**Reference:** `docs/adr/0006-failover-router-decomposition.md`

---

## Current State

`FailoverRouter` (~210 lines) at `client-sdk/src/main/java/com/lezai/threadpool/client/router/FailoverRouter.java` mixes: routing algorithms (switch on 4), candidate filtering (health + breaker every call), failover execution loop, background health-check scheduling (`healthExecutor`, `healthCheckIntervalMs`), HTTP probing (`refreshHealthOnce`), and client instance management (`clients` map).

Existing tests touching current Router: `ConfigPollingServiceTest.java`, `RemoteConfigSourcePoolManagerTest.java`, `ConfigServerClientTest.java`, `ServerNodeParserTest.java` (unchanged), `ThreadPoolPropertiesTest.java`.

---

## Target Components

| Component | Responsibility | Type |
|----------|----------------|------|
| `RoutingStrategy` | Pick one node from candidates | Interface + 4 impls |
| `ServerNode` | Single endpoint: breaker check + delegate to client | Concrete (enhanced) |
| `CircuitBreaker` | State machine with observers + scheduled cooldown | Concrete (enhanced) |
| `CircuitBreakerScheduler` | Global single-thread scheduler for all breaker cooldowns | Concrete (new) |
| `NodeManager` | Own node pool, cache candidates, react to observer callbacks | Interface + `DefaultNodeManager` |
| `HealthChecker` | Independent background probing, push changes | Interface + `DefaultHealthChecker` |
| `FailoverRouter` | Pure orchestration: select → execute → retry | Concrete (rewritten) |

---

## Task 1: RoutingStrategy interface + 4 implementations

**Files:**
- Create: `RoutingStrategy.java`, `RoundRobinStrategy.java`, `WeightedRoundRobinStrategy.java`, `RandomStrategy.java`, `FailoverStrategy.java`
- Create: `RoutingStrategyTest.java`

All in `client-sdk/src/main/java/com/lezai/threadpool/client/router/` and `.../test/.../router/`.

- [ ] **Step 1: Write the failing test** — `RoutingStrategyTest.java` with 4 test methods (one per algorithm). Each test instantiates the strategy, calls `select()` on a `List<ServerNode>`, and asserts the expected node is returned. Use `ServerNode(url, 1)` helper. The failover test verifies: primary selected first, after primary removed → secondary selected, after primary re-added → still secondary (no flap-back).

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl client-sdk -am test -Dtest=RoutingStrategyTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL — classes do not exist.

- [ ] **Step 3: Create interface + implementations**

`RoutingStrategy.java`:
```java
package com.lezai.threadpool.client.router;
import java.util.List;
public interface RoutingStrategy {
    ServerNode select(List<ServerNode> candidates);
}
```

`RoundRobinStrategy.java` uses `AtomicInteger counter` + `Math.floorMod(counter.getAndIncrement(), size)`. `WeightedRoundRobinStrategy.java` builds a weighted list (repeat each node by weight) then indexes with round-robin counter. `RandomStrategy.java` uses `ThreadLocalRandom.current().nextInt(size)`. `FailoverStrategy.java` returns `candidates.get(0)` (primary-first; NodeManager controls candidate ordering).

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -pl client-sdk -am test -Dtest=RoutingStrategyTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add client-sdk/src/main/java/com/lezai/threadpool/client/router/RoutingStrategy.java \
  client-sdk/src/main/java/com/lezai/threadpool/client/router/RoundRobinStrategy.java \
  client-sdk/src/main/java/com/lezai/threadpool/client/router/WeightedRoundRobinStrategy.java \
  client-sdk/src/main/java/com/lezai/threadpool/client/router/RandomStrategy.java \
  client-sdk/src/main/java/com/lezai/threadpool/client/router/FailoverStrategy.java \
  client-sdk/src/test/java/com/lezai/threadpool/client/router/RoutingStrategyTest.java
git commit -m "feat(client-sdk): extract RoutingStrategy interface with 4 algorithm implementations"
```

---

## Task 2: Observer callbacks + CircuitBreakerScheduler

**Files:**
- Create: `HealthStateObserver.java`, `CircuitBreakerObserver.java`, `CircuitBreakerScheduler.java`
- Create: `CircuitBreakerSchedulerTest.java`

- [ ] **Step 1: Create callback interfaces**

`HealthStateObserver.java`: `void onStatusChanged(ServerNode node, NodeHealthStatus newStatus)`
`CircuitBreakerObserver.java`: `void onBreakerStateChanged(ServerNode node, CircuitBreakerState oldState, CircuitBreakerState newState)`

- [ ] **Step 2: Write failing test + implement `CircuitBreakerScheduler`**

`CircuitBreakerSchedulerTest.java`: test that `CircuitBreakerScheduler.schedule(task, 50, MS)` fires within ~1s, and that 5 scheduled tasks all run on the SAME thread (proves single-thread sharing).

`CircuitBreakerScheduler.java`: wraps a `static final ScheduledExecutorService` (single daemon thread named `circuit-breaker-cooldown`). Expose `static ScheduledFuture<?> schedule(Runnable, long, TimeUnit)`.

- [ ] **Step 3: Run test to verify it passes**

Run: `mvn -pl client-sdk -am test -Dtest=CircuitBreakerSchedulerTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add client-sdk/src/main/java/com/lezai/threadpool/client/router/HealthStateObserver.java \
  client-sdk/src/main/java/com/lezai/threadpool/client/router/CircuitBreakerObserver.java \
  client-sdk/src/main/java/com/lezai/threadpool/client/router/CircuitBreakerScheduler.java \
  client-sdk/src/test/java/com/lezai/threadpool/client/router/CircuitBreakerSchedulerTest.java
git commit -m "feat(client-sdk): add observer callbacks and global circuit-breaker scheduler"
```

---

## Task 3: Enhance CircuitBreaker with observers + scheduled cooldown

**Files:**
- Modify: `CircuitBreaker.java`
- Create: `CircuitBreakerEnhancedTest.java`

- [ ] **Step 1: Write the failing test** — `CircuitBreakerEnhancedTest.java` with 4 tests:
  - `notifiesObserversOnStateTransition`: add observer, call `recordFailure()` twice (threshold=2), assert observer saw exactly `[OPEN]`.
  - `schedulesCooldownToHalfOpen`: threshold=1, openDurationMs=50, `recordFailure(0L)`, assert OPEN, sleep 100ms, assert HALF_OPEN.
  - `halfOpenAllowsOnlyOneProbe`: call `transitionToHalfOpenForTest()`, assert first `allowRequest()` true, second false.
  - `successResetsToClosed`: after HALF_OPEN + probe, `recordSuccess()`, assert CLOSED.

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl client-sdk -am test -Dtest=CircuitBreakerEnhancedTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL.

- [ ] **Step 3: Implement enhanced CircuitBreaker**

Key changes to existing `CircuitBreaker.java`:
- Add `private final List<CircuitBreakerObserver> observers = new CopyOnWriteArrayList<>()` and `addObserver()`.
- Add `getState()`, `getOpenedAtMs()`, `getOpenDurationMs()` getters.
- Remove lazy transition from `allowRequest()` (now just checks current state: CLOSED→true, HALF_OPEN→CAS probe, OPEN→false).
- `recordFailure()`: if HALF_OPEN or count ≥ threshold → `setState(OPEN, nowMs)`.
- `setState(OPEN, nowMs)`: schedule cooldown via `CircuitBreakerScheduler.schedule(() -> { setState(HALF_OPEN); halfOpenProbeInFlight.set(true); }, openDurationMs, MS)`.
- `setState()` always notifies observers on actual transition.
- Add package-private `transitionToHalfOpenForTest()` for testing.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -pl client-sdk -am test -Dtest=CircuitBreakerEnhancedTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add client-sdk/src/main/java/com/lezai/threadpool/client/router/CircuitBreaker.java \
  client-sdk/src/test/java/com/lezai/threadpool/client/router/CircuitBreakerEnhancedTest.java
git commit -m "feat(client-sdk): enhance CircuitBreaker with observer notifications and scheduled cooldown"
```

---

## Task 4: ServerNode implements ConfigOperations

**Files:**
- Modify: `ServerNode.java`
- Create: `ServerNodeEnhancedTest.java`

- [ ] **Step 1: Write failing test** — `ServerNodeEnhancedTest.java` with 3 tests:
  - `delegatesCallWhenBreakerClosed`: MockWebServer returns 200, create `ServerNode(name, baseUrl, 1, client, breaker)`, call `pullConfigs(1L)`, assert configVersion==1.
  - `blocksCallWhenBreakerOpen`: create node, `breaker.recordFailure(0L)`, assert `pullConfigs()` throws `IOException` containing "Circuit breaker is OPEN".
  - `recordsFailureOnIOException`: MockWebServer down, call `pullConfigs()`, assert throws AND breaker state is OPEN.

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl client-sdk -am test -Dtest=ServerNodeEnhancedTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL — 5-arg constructor does not exist.

- [ ] **Step 3: Implement enhanced ServerNode**

```java
@Getter
public class ServerNode implements ConfigOperations {
    private final String name, baseUrl;
    private final int weight;
    private final ConfigServerClient client;
    private final CircuitBreaker circuitBreaker;
    private volatile NodeHealthStatus healthStatus = NodeHealthStatus.UNKNOWN;
    private BiConsumer<CircuitBreakerState, CircuitBreakerState> breakerCallback;

    public ServerNode(String name, String baseUrl, int weight,
                      ConfigServerClient client, CircuitBreaker circuitBreaker) { ... }
    // backward-compat 2-arg ctor for existing parser tests
    public ServerNode(String baseUrl, int weight) { this(baseUrl, baseUrl, weight, new ConfigServerClient(...), new CircuitBreaker(3, 30000)); }

    void setBreakerCallback(BiConsumer<CircuitBreakerState, CircuitBreakerState> cb) { this.breakerCallback = cb; }

    // All ConfigOperations methods delegate to execute()
    private <T> T execute(IoCall<T> call) throws IOException {
        long now = System.currentTimeMillis();
        if (!circuitBreaker.allowRequest(now)) throw new NodeUnavailableException("Circuit breaker is OPEN for " + baseUrl);
        try { T r = call.apply(client); circuitBreaker.recordSuccess(); return r; }
        catch (IOException e) { circuitBreaker.recordFailure(now); throw e; }
    }

    public HealthResponse checkHealth() { return client.health(); }  // bypasses breaker
    public void markHealth(NodeHealthStatus s) { this.healthStatus = s; }
    @Override public void release() { client.release(); }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -pl client-sdk -am test -Dtest=ServerNodeEnhancedTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add client-sdk/src/main/java/com/lezai/threadpool/client/router/ServerNode.java \
  client-sdk/src/test/java/com/lezai/threadpool/client/router/ServerNodeEnhancedTest.java
git commit -m "feat(client-sdk): ServerNode implements ConfigOperations with embedded breaker"
```

---

## Task 5: NodeManager interface + DefaultNodeManager

**Files:**
- Create: `NodeManager.java`, `DefaultNodeManager.java`
- Create: `DefaultNodeManagerTest.java`

- [ ] **Step 1: Write failing test** — `DefaultNodeManagerTest.java`:
  - `getCandidates_excludesDownAndOpen`: create 3 nodes, mark one DOWN, open one breaker, assert candidates contains only the healthy+closed node.
  - `onStatusChanged_removesDownNode`: healthy candidates, then `onStatusChanged(node, DOWN)`, assert node removed.
  - `onStatusChanged_addsRecoveredNode`: node DOWN then `onStatusChanged(node, UP)`, assert node back in candidates.
  - `onBreakerStateChanged_removesOpenNode`: healthy candidates, `onBreakerStateChanged(node, CLOSED, OPEN)`, assert removed.
  - `onBreakerStateChanged_addsHalfOpenNode`: node OPEN then `onBreakerStateChanged(node, OPEN, HALF_OPEN)`, assert added back.
  - `isDegraded_trueWhenAnyDegraded`: mark one node DEGRADED, assert isDegraded() true.

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl client-sdk -am test -Dtest=DefaultNodeManagerTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL.

- [ ] **Step 3: Implement**

`NodeManager.java`:
```java
public interface NodeManager extends HealthStateObserver, CircuitBreakerObserver {
    List<ServerNode> getCandidates();
    boolean isDegraded();
    int nodeCount();
    void release();
}
```

`DefaultNodeManager.java`:
```java
public class DefaultNodeManager implements NodeManager {
    private final List<ServerNode> nodes;
    private volatile List<ServerNode> cachedCandidates;

    public DefaultNodeManager(List<ServerNode> nodes, CircuitBreakerConfig breakerConfig) {
        this.nodes = List.copyOf(nodes);
        this.cachedCandidates = nodes.stream().filter(this::isAvailable).toList();
    }

    private boolean isAvailable(ServerNode n) {
        return n.getHealthStatus() != NodeHealthStatus.DOWN
            && n.getCircuitBreaker().getState() != CircuitBreakerState.OPEN;
    }

    @Override public List<ServerNode> getCandidates() { return cachedCandidates; }

    @Override public void onStatusChanged(ServerNode node, NodeHealthStatus newStatus) {
        rebuildCandidates();
    }

    @Override public void onBreakerStateChanged(ServerNode node, CircuitBreakerState old, CircuitBreakerState newState) {
        rebuildCandidates();
    }

    private void rebuildCandidates() {
        cachedCandidates = nodes.stream().filter(this::isAvailable).toList();
    }

    @Override public boolean isDegraded() {
        return nodes.stream().anyMatch(n -> n.getHealthStatus() == NodeHealthStatus.DEGRADED);
    }

    @Override public int nodeCount() { return nodes.size(); }

    @Override public void release() { nodes.forEach(ServerNode::release); }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -pl client-sdk -am test -Dtest=DefaultNodeManagerTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add client-sdk/src/main/java/com/lezai/threadpool/client/router/NodeManager.java \
  client-sdk/src/main/java/com/lezai/threadpool/client/router/DefaultNodeManager.java \
  client-sdk/src/test/java/com/lezai/threadpool/client/router/DefaultNodeManagerTest.java
git commit -m "feat(client-sdk): add NodeManager with cached candidate list driven by observer callbacks"
```

---

## Task 6: HealthChecker interface + DefaultHealthChecker

**Files:**
- Create: `HealthChecker.java`, `HealthCheckProbe.java`, `DefaultHealthChecker.java`
- Create: `DefaultHealthCheckerTest.java`

- [ ] **Step 1: Write failing test** — `DefaultHealthCheckerTest.java`:
  - `pushesStatusChangeWhenHealthChanges`: create node with MockWebServer returning UP, mock `HealthStateObserver`, start checker with short interval (100ms), wait, assert observer received `onStatusChanged(node, UP)`.
  - `doesNotPushWhenStatusUnchanged`: node already UP, checker probes UP again, assert observer NOT called.
  - `marksDownOnException`: MockWebServer down, assert observer receives `onStatusChanged(node, DOWN)`.

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl client-sdk -am test -Dtest=DefaultHealthCheckerTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL.

- [ ] **Step 3: Implement**

`HealthChecker.java`: `void start()`, `void stop()`.
`HealthCheckProbe.java`: `NodeHealthStatus check(ServerNode node)` — bypasses breaker, calls `node.checkHealth()`, maps response to `NodeHealthStatus`.
`DefaultHealthChecker.java`: holds `List<ServerNode>`, `HealthStateObserver`, `ScheduledExecutorService`. On each tick, probes each node via `HealthCheckProbe`, compares to `node.getHealthStatus()`, calls `observer.onStatusChanged()` only on change.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -pl client-sdk -am test -Dtest=DefaultHealthCheckerTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add client-sdk/src/main/java/com/lezai/threadpool/client/router/HealthChecker.java \
  client-sdk/src/main/java/com/lezai/threadpool/client/router/HealthCheckProbe.java \
  client-sdk/src/main/java/com/lezai/threadpool/client/router/DefaultHealthChecker.java \
  client-sdk/src/test/java/com/lezai/threadpool/client/router/DefaultHealthCheckerTest.java
git commit -m "feat(client-sdk): add independent HealthChecker with push-based status updates"
```

---

## Task 7: Rewrite FailoverRouter as thin orchestration

**Files:**
- Modify: `FailoverRouter.java`
- Create: `FailoverRouterV2Test.java`

- [ ] **Step 1: Write failing test** — `FailoverRouterV2Test.java`:
  - `selectsNodeAndDelegatesCall`: mock NodeManager (returns 1 candidate), mock RoutingStrategy (returns that node), call `pullConfigs()`, verify strategy.select() called with candidates, node.pullConfigs() called.
  - `retriesOnNextNodeAfterIOException`: 2 nodes, first throws IOException, second succeeds, assert second node's result returned.
  - `throwsAfterAllNodesExhausted`: all nodes throw IOException, assert throws IOException.
  - `noCandidatesThrowsImmediately`: NodeManager returns empty candidates, assert throws IOException without calling any node.

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl client-sdk -am test -Dtest=FailoverRouterV2Test -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL — constructor signature changed.

- [ ] **Step 3: Implement thin FailoverRouter**

```java
@Slf4j
public class FailoverRouter implements ConfigOperations {
    private final NodeManager nodeManager;
    private final RoutingStrategy routingStrategy;

    public FailoverRouter(NodeManager nodeManager, RoutingStrategy routingStrategy) {
        this.nodeManager = nodeManager;
        this.routingStrategy = routingStrategy;
    }

    @Override
    public ThreadPoolConfigResp pullConfigs(Long version) throws IOException {
        return execute(node -> node.pullConfigs(version));
    }
    // subscribe / registerConfig / registerConfigs / reportStats 同理

    private <T> T execute(IoNodeCall<T> call) throws IOException {
        IOException last = null;
        for (int attempt = 0; attempt < nodeManager.nodeCount(); attempt++) {
            List<ServerNode> candidates = nodeManager.getCandidates();
            if (candidates.isEmpty()) break;
            ServerNode node = routingStrategy.select(candidates);
            try {
                return call.apply(node);
            } catch (IOException e) {
                last = e;
                log.warn("Node {} failed, failover to next (attempt {}/{})", node.getBaseUrl(), attempt + 1, nodeManager.nodeCount());
            }
        }
        throw last != null ? last : new IOException("No available admin-server node");
    }

    @Override public void release() { nodeManager.release(); }

    @FunctionalInterface private interface IoNodeCall<T> {
        T apply(ServerNode node) throws IOException;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -pl client-sdk -am test -Dtest=FailoverRouterV2Test -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add client-sdk/src/main/java/com/lezai/threadpool/client/router/FailoverRouter.java \
  client-sdk/src/test/java/com/lezai/threadpool/client/router/FailoverRouterV2Test.java
git commit -m "feat(client-sdk): rewrite FailoverRouter as thin orchestration over NodeManager and RoutingStrategy"
```

---

## Task 8: Rewrite ThreadPoolAutoConfiguration wiring

**Files:**
- Modify: `ThreadPoolAutoConfiguration.java`

- [ ] **Step 1: Rewrite `configOperations()` bean**

```java
@Bean
@ConditionalOnMissingBean
@ConditionalOnBooleanProperty(name = "thread.pool.remote.enabled")
public ConfigOperations configOperations() {
    ThreadPoolProperties.RemoteConfig remote = properties.getRemote();
    List<ServerNode> nodes = ServerNodeParser.parse(remote.getServerUrl()).stream()
        .map(n -> new ServerNode(n.getBaseUrl(), n.getWeight(),
                new ConfigServerClient(n.getBaseUrl(), remote.getAppId(), remote.getApiKey(),
                        remote.getLongPollingTimeoutMs() + 5000),
                new CircuitBreaker(remote.getCircuitBreaker().getFailureThreshold(),
                        remote.getCircuitBreaker().getOpenDurationMs())))
        .toList();

    if ("single".equalsIgnoreCase(remote.getMode())) {
        log.info("thread.pool.remote.mode=single: circuit-breaker/routing-algorithm settings apply only to cluster mode");
        return nodes.get(0);  // single node, no router needed
    }

    RoutingAlgorithm algorithm = RoutingAlgorithm.valueOf(
            remote.getRoutingAlgorithm().toUpperCase().replace('-', '_'));
    RoutingStrategy strategy = switch (algorithm) {
        case ROUND_ROBIN -> new RoundRobinStrategy();
        case WEIGHTED_ROUND_ROBIN -> new WeightedRoundRobinStrategy();
        case RANDOM -> new RandomStrategy();
        case FAILOVER -> new FailoverStrategy();
    };

    DefaultNodeManager nodeManager = new DefaultNodeManager(nodes, remote.getCircuitBreaker());
    HealthChecker healthChecker = new DefaultHealthChecker(nodes, nodeManager,
            remote.getHealthCheckIntervalMs(), remote.getHealthCheckFastIntervalMs());

    // External observer registration — no circular dependency
    nodes.forEach(n -> n.setBreakerCallback(nodeManager::onBreakerStateChanged));
    healthChecker.registerObserver(nodeManager);

    FailoverRouter router = new FailoverRouter(nodeManager, strategy);
    healthChecker.start();
    return router;
}
```

- [ ] **Step 2: Update `configPollingService()` and `threadPoolStatsReporter()` beans**

Both now take `ConfigOperations` (the FailoverRouter) and read `isDegraded()` from the NodeManager. Since NodeManager is created inside `configOperations()`, expose it as a `@