# FailoverRouter Decomposition Implementation Plan — Part 2 (Tasks 8–11)

This file continues from `2026-07-16-failover-router-decomposition.md`, which covers Tasks 1–7.

---

## Task 8: Rewrite ThreadPoolAutoConfiguration wiring (continued)

**Files:**
- Modify: `ThreadPoolAutoConfiguration.java`

- [ ] **Step 1: Rewrite `configOperations()` bean** (already written in Part 1).

- [ ] **Step 2: Expose `NodeManager` as a separate bean so other beans can read `isDegraded()`**

Since `NodeManager` is currently created inside the `configOperations()` method, make it a `@Bean`:

```java
@Bean
@ConditionalOnMissingBean
@ConditionalOnBooleanProperty(name = "thread.pool.remote.enabled")
public NodeManager nodeManager() {
    ThreadPoolProperties.RemoteConfig remote = properties.getRemote();
    List<ServerNode> nodes = ServerNodeParser.parse(remote.getServerUrl()).stream()
        .map(n -> new ServerNode(n.getBaseUrl(), n.getWeight(),
                new ConfigServerClient(n.getBaseUrl(), remote.getAppId(), remote.getApiKey(),
                        remote.getLongPollingTimeoutMs() + 5000),
                new CircuitBreaker(remote.getCircuitBreaker().getFailureThreshold(),
                        remote.getCircuitBreaker().getOpenDurationMs())))
        .toList();
    DefaultNodeManager manager = new DefaultNodeManager(nodes, remote.getCircuitBreaker());

    if (!"single".equalsIgnoreCase(remote.getMode())) {
        HealthChecker healthChecker = new DefaultHealthChecker(nodes, manager,
                remote.getHealthCheckIntervalMs(), remote.getHealthCheckFastIntervalMs());
        nodes.forEach(n -> n.setBreakerCallback(manager::onBreakerStateChanged));
        healthChecker.registerObserver(manager);
        healthChecker.start();
    }
    return manager;
}

@Bean
@ConditionalOnMissingBean
@ConditionalOnBooleanProperty(name = "thread.pool.remote.enabled")
public ConfigOperations configOperations(NodeManager nodeManager) {
    ThreadPoolProperties.RemoteConfig remote = properties.getRemote();
    if ("single".equalsIgnoreCase(remote.getMode())) {
        log.info("thread.pool.remote.mode=single: circuit-breaker/routing-algorithm settings apply only to cluster mode");
        return nodeManager.getCandidates().get(0);
    }

    RoutingAlgorithm algorithm = RoutingAlgorithm.valueOf(
            remote.getRoutingAlgorithm().toUpperCase().replace('-', '_'));
    RoutingStrategy strategy = switch (algorithm) {
        case ROUND_ROBIN -> new RoundRobinStrategy();
        case WEIGHTED_ROBIN -> new WeightedRoundRobinStrategy();
        case RANDOM -> new RandomStrategy();
        case FAILOVER -> new FailoverStrategy();
    };
    return new FailoverRouter(nodeManager, strategy);
}
```

- [ ] **Step 3: Update `configPollingService()` and `threadPoolStatsReporter()` beans**

Both now consume `BooleanSupplier` for degraded signal from `NodeManager` directly:

```java
@Bean
@ConditionalOnMissingBean
@ConditionalOnBooleanProperty(name = "thread.pool.remote.enabled")
public ConfigPollingService configPollingService(ConfigOperations configOperations,
                                                  ThreadPoolManager threadPoolManager,
                                                  NodeManager nodeManager) {
    ThreadPoolProperties.RemoteConfig remote = properties.getRemote();
    return new ConfigPollingService(configOperations, threadPoolManager, remote.getAppId(),
            remote.getLongPollingTimeoutMs(), remote.getPullIntervalMs(),
            remote.getDegraded().getPullIntervalMs(),
            remote.getBackoffInitialMs(), remote.getBackoffMaxMs(),
            nodeManager::isDegraded);
}

@Bean
@ConditionalOnMissingBean
@ConditionalOnBooleanProperty(name = "thread.pool.remote.enabled")
public ThreadPoolStatsReporter threadPoolStatsReporter(ConfigOperations configOperations,
                                                       ThreadPoolManager threadPoolManager,
                                                       NodeManager nodeManager) {
    ThreadPoolProperties.RemoteConfig remote = properties.getRemote();
    if (!remote.isReportEnabled()) { log.info("ThreadPoolStatsReporter is disabled"); return null; }
    return new ThreadPoolStatsReporter(configOperations, remote.getAppId(),
            remote.getReportIntervalMs(), remote.getDegraded().getReportIntervalMs(),
            threadPoolManager, nodeManager::isDegraded);
}
```

- [ ] **Step 4: Compile to verify**

Run: `mvn -pl client-sdk -am compile -DskipTests`
Expected: BUILD SUCCESS.

- [ ] **Step 5: Update existing tests**

`ConfigPollingServiceTest.java`: constructor already matches (degradedSupplier param present). Verify tests still pass.

`RemoteConfigSourcePoolManagerTest.java`: constructor takes `ConfigOperations` — verify still compatible.

Run: `mvn -pl client-sdk -am test -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS (all 105+ tests).

- [ ] **Step 6: Commit**

```bash
git add client-sdk/src/main/java/com/lezai/threadpool/config/ThreadPoolAutoConfiguration.java \
  client-sdk/src/test/java/com/lezai/threadpool/ConfigPollingServiceTest.java \
  client-sdk/src/test/java/com/lezai/threadpool/RemoteConfigSourcePoolManagerTest.java
git commit -m "refactor(client-sdk): rewrite auto-configuration to wire decomposed components"
```

---

## Task 9: Add NodeHealthStatus mapping utility

**Files:**
- Create: `client-sdk/src/main/java/com/lezai/threadpool/client/router/HealthStatusMapper.java`
- Create: `client-sdk/src/test/java/com/lezai/threadpool/client/router/HealthStatusMapperTest.java`

- [ ] **Step 1: Write failing test** — test that `"UP"` maps to `UP`, `"DEGRADED"` maps to `DEGRADED`, `"DOWN"` maps to `DOWN`, and any other value maps to `UNKNOWN`.

- [ ] **Step 2: Implement**

```java
package com.lezai.threadpool.client.router;
public final class HealthStatusMapper {
    private HealthStatusMapper() {}
    public static NodeHealthStatus fromResponse(String status) {
        if (status == null) return NodeHealthStatus.UNKNOWN;
        return switch (status.toUpperCase()) {
            case "UP" -> NodeHealthStatus.UP;
            case "DEGRADED" -> NodeHealthStatus.DEGRADED;
            case "DOWN" -> NodeHealthStatus.DOWN;
            default -> NodeHealthStatus.UNKNOWN;
        };
    }
}
```

- [ ] **Step 3: Run test + commit**

Run: `mvn -pl client-sdk -am test -Dtest=HealthStatusMapperTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS.

```bash
git add client-sdk/src/main/java/com/lezai/threadpool/client/router/HealthStatusMapper.java \
  client-sdk/src/test/java/com/lezai/threadpool/client/router/HealthStatusMapperTest.java
git commit -m "feat(client-sdk): add HealthStatusMapper utility"
```

---

## Task 10: Full test suite verification

**Goal:** ensure no regressions across both modules.

- [ ] **Step 1: Run full client-sdk suite**

Run: `mvn -pl client-sdk -am test -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS (all tests including new RoutingStrategyTest, CircuitBreakerEnhancedTest, CircuitBreakerSchedulerTest, DefaultNodeManagerTest, DefaultHealthCheckerTest, ServerNodeEnhancedTest, FailoverRouterV2Test, HealthStatusMapperTest).

- [ ] **Step 2: Run admin-server suite (excluding pre-existing E2E failures)**

Run: `mvn -pl admin-server -am test -Dtest='!AdminAuthE2ETest,!AdminServerE2ETest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: 0 failures (the 27 AdminServerE2ETest failures are pre-existing Redisson NPE issues unrelated to this refactor — verified by git stash test on baseline).

- [ ] **Step 3: Fix any compilation or test failures discovered**

If any test breaks due to the new structure, update the test to match the new API. Do NOT weaken assertions to make tests pass — fix the test to properly exercise the new component boundaries.

- [ ] **Step 4: Commit any test fixes**

```bash
git add -A
git commit -m "test(client-sdk): verify full suite passes after decomposition"
```

---

## Task 11: Documentation + ADR status update

**Files:**
- Modify: `docs/adr/0006-failover-router-decomposition.md`

- [ ] **Step 1: Update ADR status block**

Change the top of `0006-failover-router-decomposition.md` from:
```
> **Status**: Accepted
```
to:
```
> **Status**: Accepted — Implemented (v3.2, commit <HEAD_SHA>)
```

- [ ] **Step 2: Commit**

```bash
git add docs/adr/0006-failover-router-decomposition.md
git commit -m "docs: mark ADR-0006 as implemented"
```

---

## Self-Review

### Spec coverage (vs ADR-0006)
- RoutingStrategy interface + 4 impls → Task 1 ✅
- HealthChecker independent with Observer push (NodeStatusObserver) → Task 6 ✅
- CircuitBreaker with observers + scheduled cooldown → Task 2 (scheduler) + Task 3 (CB enhanced) ✅
- NodeManager with cached candidates + dual observer (health + breaker) → Task 5 ✅
- ServerNode implements ConfigOperations with breaker check → Task 4 ✅
- FailoverRouter thin orchestration → Task 7 ✅
- External observer registration (no circular dep) → Task 8 ✅
- Re-admission at OPEN→HALF_OPEN → Task 3 (scheduler) + Task 5 (onBreakerStateChanged) ✅
- Single-thread shared scheduler → Task 2 ✅

### Placeholder scan
- No TBD/TODO/fill-in-later patterns. All code steps have complete snippets or precise behavioral descriptions where boilerplate would be redundant (e.g., "subscribe/registerConfig/registerConfigs/reportStats 同理").

### Type consistency
- `NodeHealthStatus` enum: UNKNOWN/UP/DEGRADED/DOWN (consistent across HealthStatusMapper, ServerNode, NodeManager, HealthChecker)
- `CircuitBreakerState` enum: CLOSED/OPEN/HALF_OPEN (consistent across CircuitBreaker, ServerNode, NodeManager, FailoverRouter)
- `CircuitBreakerObserver.onBreakerStateChanged(ServerNode, old, new)` — Task 3 uses the `@FunctionalInterface` nested in CircuitBreaker for tests but the standalone interface from Task 2 for production wiring via `ServerNode.setBreakerCallback(BiConsumer<...>)`. Note: the `BiConsumer<CircuitBreakerState, CircuitBreakerState>` on ServerNode is internal; the external `CircuitBreakerObserver` interface is implemented by NodeManager when it calls `node.setBreakerCallback(nodeManager::onBreakerStateChanged)`.
- `RoutingStrategy.select(List<ServerNode>)` — consistent across all 4 impls and FailoverRouter usage.
- `NodeManager extends HealthStateObserver, CircuitBreakerObserver` — both interfaces implemented in DefaultNodeManager.

### Note on test code in Task 3
The test `CircuitBreakerEnhancedTest` uses `CircuitBreaker.CircuitBreakerObserver` functional interface (the nested one). This is compatible because `addObserver()` accepts it, and the production wiring in Task 4 uses `setBreakerCallback(BiConsumer<...>)` which is separate from the observer list used in tests. Both paths trigger `notifyListeners`.
