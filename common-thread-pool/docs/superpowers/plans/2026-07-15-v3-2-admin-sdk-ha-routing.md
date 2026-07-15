# v3.2 Admin + SDK HA Routing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver the v3.2 admin-server + client-sdk backlog: dashboard alerting, transaction hardening, stats aggregation, health checks, client-side admin-server HA routing, pull path migration, delete notification fix, and e2e coverage.

**Architecture:** Keep admin-server storage and HTTP contracts stable while adding focused services/controllers around the existing repository/storage layers. Keep `ConfigServerClient` as a single-node HTTP façade and introduce a separate `FailoverRouter` that owns node selection, health, failover, circuit breaking, and DEGRADED scheduling signals.

**Tech Stack:** Java 21, Spring Boot 3.x, MyBatis-Plus, Redisson, Caffeine, MapStruct, Lombok, OkHttp MockWebServer, JUnit 5, AssertJ, Mockito, Maven.

---

## Scope and Execution Order

This plan covers the full v3.2 task list from `docs/v3.0-task-breakdown.md` and `docs/adr/0005-client-side-admin-server-ha-routing.md`. It intentionally keeps each task independently testable and committable.

Recommended order:

1. Admin quick wins: T-05, T-06, T-07
2. Admin transactional/storage work: T-09, T-11, T-15
3. Admin HTTP contracts/UI: T-13a, T-14, T-10/T-12
4. SDK HA Router: T-13b
5. Integration scripts/tests: T-13c, T-18

## File Structure

### Admin-server backend

- Create `admin-server/src/main/java/com/lezai/threadpool/pojo/bean/PoolAlert.java` — immutable alert DTO used by backend and dashboard JSON responses.
- Create `admin-server/src/main/java/com/lezai/threadpool/pojo/response/DashboardSummaryResponse.java` — typed dashboard summary response replacing the raw map.
- Create `admin-server/src/main/java/com/lezai/threadpool/pojo/response/HealthResponse.java` — trivalent health response for `/open/api/thread-pool/health`.
- Create `admin-server/src/main/java/com/lezai/threadpool/enums/HealthState.java` — `UP`, `DEGRADED`, `DOWN`, `N_A` enum values serialized as strings.
- Modify `admin-server/src/main/java/com/lezai/threadpool/enums/BizType.java` — add `THREAD_POOL_STATS`.
- Modify `admin-server/src/main/java/com/lezai/threadpool/enums/OperateType.java` — add `ALERT` and `ALERT_RECOVERED`.
- Modify `admin-server/src/main/java/com/lezai/threadpool/service/ThreadPoolStatsPersistenceService.java` — add recent stats query and aggregation SQL helpers.
- Create `admin-server/src/main/java/com/lezai/threadpool/service/StatsAlertService.java` — compute alert/recovery state and write operate logs.
- Modify `admin-server/src/main/java/com/lezai/threadpool/service/OperateLogService.java` — add offset/limit recent log query.
- Modify `admin-server/src/main/java/com/lezai/threadpool/controller/DashboardController.java` — return typed summary with alert count, alert list, paged logs, and `hasMore`.
- Modify `admin-server/src/main/java/com/lezai/threadpool/service/ThreadPoolConfigPersistenceService.java` — add missing `rollbackFor = Exception.class`.
- Modify `admin-server/src/main/java/com/lezai/threadpool/service/ConfigAdminService.java` — add transaction boundaries to write operations.
- Modify `admin-server/src/main/java/com/lezai/threadpool/storage/MyBatisConfigStorage.java` — trigger final delete notification before unregistering listeners.
- Modify `admin-server/src/main/java/com/lezai/threadpool/util/SyncLock.java` — add `tryLock` contract.
- Modify `admin-server/src/main/java/com/lezai/threadpool/util/LocalStripedLock.java` — implement local `tryLock`.
- Modify `admin-server/src/main/java/com/lezai/threadpool/util/RedissonSyncLock.java` — implement Redisson `tryLock`.
- Create `admin-server/src/main/java/com/lezai/threadpool/service/StatsAggregationService.java` — scheduled daily aggregation and cleanup.
- Modify `admin-server/src/main/java/com/lezai/threadpool/ThreadPoolAdminServer.java` — enable scheduling.
- Modify `admin-server/src/main/resources/schema.sql` — add `thread_pool_stats_daily` table.
- Create `admin-server/src/main/java/com/lezai/threadpool/service/AdminHealthService.java` — DB/Redis checks for trivalent health.
- Create `admin-server/src/main/java/com/lezai/threadpool/open/HealthController.java` — public unauthenticated health endpoint.
- Modify `admin-server/src/main/java/com/lezai/threadpool/open/OpenThreadPoolConfigController.java` — add plural `/configs/{appId}/pull`; return 410 for legacy singular path.

### Admin-server frontend

- Modify `admin-server/src/main/resources/static/index.html` — add alert metric card, alert list, and load-more button for logs.
- Modify `admin-server/src/main/resources/static/js/common.js` — add shared dashboard fetch/render helpers if current page code centralizes API calls there.
- Modify `admin-server/src/main/resources/static/css/common.css` or component CSS files — add alert badge/card styles matching existing tokens.

### Client SDK HA Router

- Modify `client-sdk/src/main/java/com/lezai/threadpool/properties/ThreadPoolProperties.java` — add remote mode, routing, health, circuit breaker, and degraded intervals with validation.
- Create `client-sdk/src/main/java/com/lezai/threadpool/client/router/RemoteMode.java` — `SINGLE`, `CLUSTER`.
- Create `client-sdk/src/main/java/com/lezai/threadpool/client/router/RoutingAlgorithm.java` — `ROUND_ROBIN`, `WEIGHTED_ROUND_ROBIN`, `RANDOM`, `FAILOVER`.
- Create `client-sdk/src/main/java/com/lezai/threadpool/client/router/NodeHealthStatus.java` — `UP`, `DEGRADED`, `DOWN`, `UNKNOWN`.
- Create `client-sdk/src/main/java/com/lezai/threadpool/client/router/CircuitBreakerState.java` — `CLOSED`, `OPEN`, `HALF_OPEN`.
- Create `client-sdk/src/main/java/com/lezai/threadpool/client/router/ServerNode.java` — parsed URL, weight, health, circuit breaker, and probe state.
- Create `client-sdk/src/main/java/com/lezai/threadpool/client/router/ServerNodeParser.java` — parse comma-separated `server-url` with optional weight.
- Create `client-sdk/src/main/java/com/lezai/threadpool/client/router/CircuitBreaker.java` — node-level state machine.
- Create `client-sdk/src/main/java/com/lezai/threadpool/client/router/HealthResponse.java` — SDK-side DTO for admin health response.
- Create `client-sdk/src/main/java/com/lezai/threadpool/client/router/FailoverRouter.java` — route all client calls with failover, health refresh, and degraded signal.
- Modify `client-sdk/src/main/java/com/lezai/threadpool/client/ConfigServerClient.java` — keep single-node HTTP methods, add `reportStats`, `health`, and status-aware exceptions.
- Modify `client-sdk/src/main/java/com/lezai/threadpool/client/ConfigPollingService.java` — depend on `FailoverRouter`, dynamic pull interval, no subscribe degradation.
- Modify `client-sdk/src/main/java/com/lezai/threadpool/client/ThreadPoolStatsReporter.java` — use `FailoverRouter.reportStats`, dynamic degraded interval, no OkHttp duplication.
- Modify `client-sdk/src/main/java/com/lezai/threadpool/config/ThreadPoolAutoConfiguration.java` — create router bean and wire polling/reporter/manager through it.

### Tests and integration

- Create or modify admin tests under `admin-server/src/test/java/com/lezai/threadpool/...` for each service/controller change.
- Create SDK router tests under `client-sdk/src/test/java/com/lezai/threadpool/client/router/...`.
- Modify SDK contract tests using MockWebServer for pull path, stats report routing, failover matrix, circuit breaker, and degraded intervals.
- Create `e2e_tests_ha.py` at repo root for the 9 HA scenarios.
- Extend `e2e_tests.py` at repo root for the 6 core v3.2 chain scenarios.

---

## Task 1: Add stats alert service (T-05)

**Files:**
- Create: `admin-server/src/main/java/com/lezai/threadpool/pojo/bean/PoolAlert.java`
- Modify: `admin-server/src/main/java/com/lezai/threadpool/enums/BizType.java`
- Modify: `admin-server/src/main/java/com/lezai/threadpool/enums/OperateType.java`
- Modify: `admin-server/src/main/java/com/lezai/threadpool/service/ThreadPoolStatsPersistenceService.java`
- Create: `admin-server/src/main/java/com/lezai/threadpool/service/StatsAlertService.java`
- Test: `admin-server/src/test/java/com/lezai/threadpool/service/StatsAlertServiceTest.java`

- [ ] **Step 1: Add alert DTO and enum values**

Add `PoolAlert.java`:

```java
package com.lezai.threadpool.pojo.bean;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class PoolAlert {
    private String appId;
    private String poolName;
    private String metric;
    private double value;
    private double threshold;
    private LocalDateTime detectedAt;
    private String message;
}
```

Update `BizType.java`:

```java
public enum BizType {
    APIKEY,
    THREADPOOL_CONFIG,
    THREAD_POOL_STATS
}
```

Update `OperateType.java`:

```java
public enum OperateType {
    CREATE,
    UPDATE,
    DELETE,
    REGENERATE,
    UPSERT,
    ROLLBACK,
    ALERT,
    ALERT_RECOVERED
}
```

- [ ] **Step 2: Add recent stats query**

In `ThreadPoolStatsPersistenceService`, add this method:

```java
public List<ThreadPoolStatsEntity> queryRecentStats(LocalDateTime since) {
    LocalDateTime begin = since != null ? since : LocalDateTime.now().minusMinutes(5);
    return list(Wrappers.<ThreadPoolStatsEntity>lambdaQuery()
            .ge(ThreadPoolStatsEntity::getCollectTime, begin)
            .orderByAsc(ThreadPoolStatsEntity::getAppId)
            .orderByAsc(ThreadPoolStatsEntity::getPoolName)
            .orderByAsc(ThreadPoolStatsEntity::getCollectTime)
            .last("LIMIT 5000"));
}
```

- [ ] **Step 3: Write failing tests for alert calculations**

Create `StatsAlertServiceTest.java`:

```java
package com.lezai.threadpool.service;

import com.lezai.threadpool.dao.entity.ThreadPoolStatsEntity;
import com.lezai.threadpool.enums.BizType;
import com.lezai.threadpool.enums.OperateType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatsAlertServiceTest {

    @Mock
    private ThreadPoolStatsPersistenceService statsPersistenceService;

    @Mock
    private OperateLogService operateLogService;

    private StatsAlertService service;

    @BeforeEach
    void setUp() {
        service = new StatsAlertService(statsPersistenceService, operateLogService);
    }

    @Test
    @DisplayName("拒绝率超过 5% 时产生告警并写审计日志")
    void rejectionRateAlert() {
        when(statsPersistenceService.queryRecentStats(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(stats("app1", "pool-a", 100, 6, 10, 100)));

        var alerts = service.checkAlerts();

        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).getMetric()).isEqualTo("rejectionRate");
        assertThat(alerts.get(0).getValue()).isEqualTo(0.06d);
        verify(operateLogService).log(eq(OperateType.ALERT), eq("system"), eq(alerts.get(0)), eq("app1:pool-a:rejectionRate"), eq(BizType.THREAD_POOL_STATS));
    }

    @Test
    @DisplayName("队列使用率超过 80% 时产生告警")
    void queueUsageAlert() {
        when(statsPersistenceService.queryRecentStats(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(stats("app1", "pool-a", 100, 0, 81, 100)));

        var alerts = service.checkAlerts();

        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).getMetric()).isEqualTo("queueUsageRate");
        assertThat(alerts.get(0).getValue()).isEqualTo(0.81d);
    }

    @Test
    @DisplayName("同一 app/pool/metric 30 分钟内不重复写告警日志")
    void deduplicateWithinThirtyMinutes() {
        when(statsPersistenceService.queryRecentStats(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(stats("app1", "pool-a", 100, 6, 10, 100)));

        service.checkAlerts();
        service.checkAlerts();

        verify(operateLogService, org.mockito.Mockito.times(1))
                .log(eq(OperateType.ALERT), eq("system"), org.mockito.ArgumentMatchers.any(), eq("app1:pool-a:rejectionRate"), eq(BizType.THREAD_POOL_STATS));
    }

    @Test
    @DisplayName("告警恢复时写 ALERT_RECOVERED 日志")
    void recoveredAlertWritesLog() {
        when(statsPersistenceService.queryRecentStats(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(stats("app1", "pool-a", 100, 6, 10, 100)))
                .thenReturn(List.of(stats("app1", "pool-a", 100, 0, 10, 100)));

        service.checkAlerts();
        var recovered = service.checkAlerts();

        assertThat(recovered).isEmpty();
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(operateLogService).log(eq(OperateType.ALERT_RECOVERED), eq("system"), captor.capture(), eq("app1:pool-a:rejectionRate"), eq(BizType.THREAD_POOL_STATS));
    }

    @Test
    @DisplayName("没有异常指标时不写告警日志")
    void noAlert() {
        when(statsPersistenceService.queryRecentStats(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(stats("app1", "pool-a", 100, 0, 10, 100)));

        var alerts = service.checkAlerts();

        assertThat(alerts).isEmpty();
        verify(operateLogService, never()).log(eq(OperateType.ALERT), eq("system"), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), eq(BizType.THREAD_POOL_STATS));
    }

    private ThreadPoolStatsEntity stats(String appId, String poolName, long submitted, long rejected,
                                        int queueSize, int queueCapacity) {
        ThreadPoolStatsEntity entity = new ThreadPoolStatsEntity();
        entity.setAppId(appId);
        entity.setPoolName(poolName);
        entity.setSubmittedTaskCount(submitted);
        entity.setRejectedTaskCount(rejected);
        entity.setQueueSize(queueSize);
        entity.setQueueCapacity(queueCapacity);
        entity.setCollectTime(LocalDateTime.now());
        return entity;
    }
}
```

- [ ] **Step 4: Run the failing test**

Run:

```bash
mvn -pl admin-server -am test -Dtest=StatsAlertServiceTest
```

Expected: compilation fails because `StatsAlertService` does not exist yet.

- [ ] **Step 5: Implement `StatsAlertService`**

Create `StatsAlertService.java`:

```java
package com.lezai.threadpool.service;

import com.lezai.threadpool.dao.entity.ThreadPoolStatsEntity;
import com.lezai.threadpool.enums.BizType;
import com.lezai.threadpool.enums.OperateType;
import com.lezai.threadpool.pojo.bean.PoolAlert;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatsAlertService {

    static final double REJECTION_RATE_THRESHOLD = 0.05d;
    static final double QUEUE_USAGE_THRESHOLD = 0.80d;

    private final ThreadPoolStatsPersistenceService statsPersistenceService;
    private final OperateLogService operateLogService;
    private final Map<String, LocalDateTime> lastAlertTime = new HashMap<>();
    private final Map<String, PoolAlert> activeAlerts = new HashMap<>();

    public synchronized List<PoolAlert> checkAlerts() {
        LocalDateTime now = LocalDateTime.now();
        List<ThreadPoolStatsEntity> recentStats = statsPersistenceService.queryRecentStats(now.minusMinutes(5));
        Map<String, List<ThreadPoolStatsEntity>> grouped = recentStats.stream()
                .filter(stat -> stat.getAppId() != null && stat.getPoolName() != null)
                .collect(Collectors.groupingBy(stat -> stat.getAppId() + ":" + stat.getPoolName()));

        List<PoolAlert> alerts = new ArrayList<>();
        for (Map.Entry<String, List<ThreadPoolStatsEntity>> entry : grouped.entrySet()) {
            alerts.addAll(checkPool(entry.getValue(), now));
        }
        recoverMissingAlerts(alerts, now);
        return alerts;
    }

    private List<PoolAlert> checkPool(List<ThreadPoolStatsEntity> stats, LocalDateTime now) {
        ThreadPoolStatsEntity first = stats.get(0);
        List<PoolAlert> alerts = new ArrayList<>();
        long submitted = stats.stream().mapToLong(ThreadPoolStatsEntity::getSubmittedTaskCount).sum();
        long rejected = stats.stream().mapToLong(ThreadPoolStatsEntity::getRejectedTaskCount).sum();
        double rejectionRate = submitted > 0 ? (double) rejected / submitted : 0.0d;
        if (rejectionRate > REJECTION_RATE_THRESHOLD) {
            alerts.add(alert(first.getAppId(), first.getPoolName(), "rejectionRate", rejectionRate,
                    REJECTION_RATE_THRESHOLD, now, "线程池拒绝率超过阈值"));
        }

        double queueUsage = stats.stream()
                .filter(stat -> stat.getQueueCapacity() > 0)
                .mapToDouble(stat -> (double) stat.getQueueSize() / stat.getQueueCapacity())
                .average()
                .orElse(0.0d);
        if (queueUsage > QUEUE_USAGE_THRESHOLD) {
            alerts.add(alert(first.getAppId(), first.getPoolName(), "queueUsageRate", queueUsage,
                    QUEUE_USAGE_THRESHOLD, now, "线程池队列使用率超过阈值"));
        }

        for (PoolAlert alert : alerts) {
            recordAlertIfNeeded(alert, now);
        }
        return alerts;
    }

    private PoolAlert alert(String appId, String poolName, String metric, double value, double threshold,
                            LocalDateTime detectedAt, String message) {
        return PoolAlert.builder()
                .appId(appId)
                .poolName(poolName)
                .metric(metric)
                .value(value)
                .threshold(threshold)
                .detectedAt(detectedAt)
                .message(message)
                .build();
    }

    private void recordAlertIfNeeded(PoolAlert alert, LocalDateTime now) {
        String key = key(alert);
        activeAlerts.put(key, alert);
        LocalDateTime last = lastAlertTime.get(key);
        if (last == null || last.plusMinutes(30).isBefore(now)) {
            operateLogService.log(OperateType.ALERT, "system", alert, key, BizType.THREAD_POOL_STATS);
            lastAlertTime.put(key, now);
        }
    }

    private void recoverMissingAlerts(List<PoolAlert> currentAlerts, LocalDateTime now) {
        List<String> currentKeys = currentAlerts.stream().map(this::key).toList();
        List<String> recoveredKeys = activeAlerts.keySet().stream()
                .filter(key -> !currentKeys.contains(key))
                .toList();
        for (String key : recoveredKeys) {
            PoolAlert recovered = activeAlerts.remove(key);
            if (recovered != null) {
                operateLogService.log(OperateType.ALERT_RECOVERED, "system", recovered, key, BizType.THREAD_POOL_STATS);
                lastAlertTime.remove(key);
                log.info("Thread pool alert recovered: {}, time={}", key, now);
            }
        }
    }

    private String key(PoolAlert alert) {
        return String.join(":", alert.getAppId(), alert.getPoolName(), alert.getMetric());
    }
}
```

- [ ] **Step 6: Run alert tests and commit**

Run:

```bash
mvn -pl admin-server -am test -Dtest=StatsAlertServiceTest
```

Expected: PASS.

Commit:

```bash
git add admin-server/src/main/java/com/lezai/threadpool/pojo/bean/PoolAlert.java \
  admin-server/src/main/java/com/lezai/threadpool/enums/BizType.java \
  admin-server/src/main/java/com/lezai/threadpool/enums/OperateType.java \
  admin-server/src/main/java/com/lezai/threadpool/service/ThreadPoolStatsPersistenceService.java \
  admin-server/src/main/java/com/lezai/threadpool/service/StatsAlertService.java \
  admin-server/src/test/java/com/lezai/threadpool/service/StatsAlertServiceTest.java
git commit -m "feat(admin-server): add thread pool stats alert service"
```

---

## Task 2: Extend dashboard summary API (T-06)

**Files:**
- Create: `admin-server/src/main/java/com/lezai/threadpool/pojo/response/DashboardSummaryResponse.java`
- Modify: `admin-server/src/main/java/com/lezai/threadpool/service/OperateLogService.java`
- Modify: `admin-server/src/main/java/com/lezai/threadpool/controller/DashboardController.java`
- Test: `admin-server/src/test/java/com/lezai/threadpool/controller/DashboardControllerTest.java`

- [ ] **Step 1: Add typed dashboard response**

Create `DashboardSummaryResponse.java`:

```java
package com.lezai.threadpool.pojo.response;

import com.lezai.threadpool.pojo.bean.PoolAlert;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DashboardSummaryResponse {
    private int appCount;
    private int apiKeyCount;
    private int configCount;
    private int alertCount;
    private boolean hasMore;
    private List<PoolAlert> alerts;
    private List<OperateLogResponse> recentLogs;
}
```

- [ ] **Step 2: Add offset/limit log query**

In `OperateLogService`, keep `getRecentLogs(int limit)` and add:

```java
public List<OperateLogResponse> getRecentLogs(int limit, int offset) {
    int safeLimit = Math.max(1, Math.min(limit, 100));
    int safeOffset = Math.max(0, offset);
    List<OperateLogEntity> entities = list(Wrappers.<OperateLogEntity>lambdaQuery()
            .orderByDesc(OperateLogEntity::getCreateTime)
            .last("LIMIT " + safeLimit + " OFFSET " + safeOffset));
    return operateLogConverter.convert(entities);
}
```

- [ ] **Step 3: Update dashboard controller**

Replace `summary()` in `DashboardController` with:

```java
@GetMapping("/summary")
public ApiResponse<DashboardSummaryResponse> summary(
        @RequestParam(defaultValue = "20") int limit,
        @RequestParam(defaultValue = "0") int offset) {
    int safeLimit = Math.max(1, Math.min(limit, 100));
    int safeOffset = Math.max(0, offset);
    List<AppConfigSummary> apps = configAdminService.listApps();
    List<ApiKeyInfoResponse> apiKeys = apiKeyAdminService.allApiKeys();
    List<OperateLogResponse> recentLogs = operateLogService.getRecentLogs(safeLimit + 1, safeOffset);
    List<PoolAlert> alerts = statsAlertService.checkAlerts();

    int configCount = apps.stream().mapToInt(AppConfigSummary::getPoolCount).sum();
    boolean hasMore = recentLogs.size() > safeLimit;
    List<OperateLogResponse> pageLogs = hasMore ? recentLogs.subList(0, safeLimit) : recentLogs;

    return ApiResponse.success(DashboardSummaryResponse.builder()
            .appCount(apps.size())
            .apiKeyCount(apiKeys.size())
            .configCount(configCount)
            .alertCount(alerts.size())
            .alerts(alerts)
            .recentLogs(pageLogs)
            .hasMore(hasMore)
            .build());
}
```

Add field to controller:

```java
private final StatsAlertService statsAlertService;
```

- [ ] **Step 4: Write controller tests**

Create `DashboardControllerTest.java`:

```java
package com.lezai.threadpool.controller;

import com.lezai.threadpool.pojo.bean.AppConfigSummary;
import com.lezai.threadpool.pojo.bean.PoolAlert;
import com.lezai.threadpool.pojo.response.ApiKeyInfoResponse;
import com.lezai.threadpool.pojo.response.OperateLogResponse;
import com.lezai.threadpool.service.ApiKeyAdminService;
import com.lezai.threadpool.service.ConfigAdminService;
import com.lezai.threadpool.service.OperateLogService;
import com.lezai.threadpool.service.StatsAlertService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DashboardControllerTest {

    @Mock private ConfigAdminService configAdminService;
    @Mock private ApiKeyAdminService apiKeyAdminService;
    @Mock private OperateLogService operateLogService;
    @Mock private StatsAlertService statsAlertService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new DashboardController(
                configAdminService, apiKeyAdminService, operateLogService, statsAlertService)).build();
    }

    @Test
    @DisplayName("summary returns alertCount, alerts, logs and hasMore")
    void summaryReturnsExtendedFields() throws Exception {
        when(configAdminService.listApps()).thenReturn(List.of(
                AppConfigSummary.builder().appId("app1").poolCount(2).configVersion(1).build()));
        when(apiKeyAdminService.allApiKeys()).thenReturn(List.of(new ApiKeyInfoResponse()));
        OperateLogResponse log1 = new OperateLogResponse();
        OperateLogResponse log2 = new OperateLogResponse();
        when(operateLogService.getRecentLogs(2, 0)).thenReturn(List.of(log1, log2));
        when(statsAlertService.checkAlerts()).thenReturn(List.of(PoolAlert.builder()
                .appId("app1").poolName("pool-a").metric("rejectionRate")
                .value(0.06d).threshold(0.05d).detectedAt(LocalDateTime.now()).build()));

        mockMvc.perform(get("/api/dashboard/summary").param("limit", "1").param("offset", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.appCount").value(1))
                .andExpect(jsonPath("$.data.apiKeyCount").value(1))
                .andExpect(jsonPath("$.data.configCount").value(2))
                .andExpect(jsonPath("$.data.alertCount").value(1))
                .andExpect(jsonPath("$.data.hasMore").value(true))
                .andExpect(jsonPath("$.data.recentLogs.length()").value(1))
                .andExpect(jsonPath("$.data.alerts[0].poolName").value("pool-a"));

        verify(operateLogService).getRecentLogs(2, 0);
    }
}
```

- [ ] **Step 5: Run dashboard tests and commit**

Run:

```bash
mvn -pl admin-server -am test -Dtest=DashboardControllerTest
```

Expected: PASS.

Commit:

```bash
git add admin-server/src/main/java/com/lezai/threadpool/pojo/response/DashboardSummaryResponse.java \
  admin-server/src/main/java/com/lezai/threadpool/service/OperateLogService.java \
  admin-server/src/main/java/com/lezai/threadpool/controller/DashboardController.java \
  admin-server/src/test/java/com/lezai/threadpool/controller/DashboardControllerTest.java
git commit -m "feat(admin-server): extend dashboard summary metrics"
```

---

## Task 3: Fix remaining transaction annotations (T-07)

**Files:**
- Modify: `admin-server/src/main/java/com/lezai/threadpool/service/ThreadPoolConfigPersistenceService.java:43,199,210`
- Test: existing admin-server tests plus grep verification

- [ ] **Step 1: Update the three annotations**

Change:

```java
@Transactional
public void createAppEntry(String appId) {
```

To:

```java
@Transactional(rollbackFor = Exception.class)
public void createAppEntry(String appId) {
```

Change:

```java
@Transactional
public ThreadPoolConfigEntity restoreConfigByAppIdAndPoolName(String appId, String poolName) {
```

To:

```java
@Transactional(rollbackFor = Exception.class)
public ThreadPoolConfigEntity restoreConfigByAppIdAndPoolName(String appId, String poolName) {
```

Change:

```java
@Transactional
public void restoreConfigsByAppId(String appId) {
```

To:

```java
@Transactional(rollbackFor = Exception.class)
public void restoreConfigsByAppId(String appId) {
```

- [ ] **Step 2: Verify no bare transactional annotations remain**

Run:

```bash
python - <<'PY'
from pathlib import Path
bad=[]
for p in Path('admin-server/src/main/java').rglob('*.java'):
    for i,line in enumerate(p.read_text(encoding='utf-8').splitlines(),1):
        if line.strip() == '@Transactional':
            bad.append(f'{p}:{i}:{line.strip()}')
print('\n'.join(bad))
raise SystemExit(1 if bad else 0)
PY
```

Expected: no output and exit code 0.

- [ ] **Step 3: Run persistence tests and commit**

Run:

```bash
mvn -pl admin-server -am test -Dtest=ThreadPoolConfigPersistenceServiceTest,ThreadPoolConfigServiceTest,ConfigAdminServiceTest
```

Expected: PASS. If `ThreadPoolConfigPersistenceServiceTest` does not exist yet, Maven reports no matching test for that class; rerun the existing two:

```bash
mvn -pl admin-server -am test -Dtest=ThreadPoolConfigServiceTest,ConfigAdminServiceTest
```

Commit:

```bash
git add admin-server/src/main/java/com/lezai/threadpool/service/ThreadPoolConfigPersistenceService.java
git commit -m "fix(admin-server): add rollbackFor to config transactions"
```

---

## Task 4: Add ConfigAdminService transaction protection (T-09)

**Files:**
- Modify: `admin-server/src/main/java/com/lezai/threadpool/service/ConfigAdminService.java`
- Test: `admin-server/src/test/java/com/lezai/threadpool/service/ConfigAdminServiceTest.java`

- [ ] **Step 1: Add transaction annotations to write operations**

Add import:

```java
import org.springframework.transaction.annotation.Transactional;
```

Annotate these methods:

```java
@Transactional(rollbackFor = Exception.class)
public void saveConfig(String appId, ThreadPoolConfig config) {
```

```java
@Transactional(rollbackFor = Exception.class)
public void saveConfigs(String appId, List<ThreadPoolConfig> configs) {
```

```java
@Transactional(rollbackFor = Exception.class)
public void deleteConfigs(String appId) {
```

```java
@Transactional(rollbackFor = Exception.class)
public void deleteConfig(String appId, String poolName) {
```

```java
@Transactional(rollbackFor = Exception.class)
public ThreadPoolConfig rollback(String appId, String poolName, long version) {
```

```java
@Transactional(rollbackFor = Exception.class)
public CreateApiKeyResponse createApp(CreateAppRequest request) {
```

```java
@Transactional(rollbackFor = Exception.class)
public void deleteApp(String appId) {
```

```java
@Transactional(rollbackFor = Exception.class)
public ThreadPoolConfigItemResponse restoreConfig(String appId, String poolName) {
```

```java
@Transactional(rollbackFor = Exception.class)
public void restoreConfigs(String appId) {
```

- [ ] **Step 2: Add reflection tests for transactional contract**

Append to `ConfigAdminServiceTest.java`:

```java
@Test
@DisplayName("write methods have rollbackFor Exception transactional boundary")
void writeMethodsHaveRollbackForException() throws Exception {
    assertTransactional("saveConfig", String.class, ThreadPoolConfig.class);
    assertTransactional("saveConfigs", String.class, List.class);
    assertTransactional("deleteConfigs", String.class);
    assertTransactional("deleteConfig", String.class, String.class);
    assertTransactional("rollback", String.class, String.class, long.class);
    assertTransactional("createApp", com.lezai.threadpool.pojo.request.CreateAppRequest.class);
    assertTransactional("deleteApp", String.class);
    assertTransactional("restoreConfig", String.class, String.class);
    assertTransactional("restoreConfigs", String.class);
}

private void assertTransactional(String methodName, Class<?>... paramTypes) throws Exception {
    var annotation = ConfigAdminService.class.getMethod(methodName, paramTypes)
            .getAnnotation(org.springframework.transaction.annotation.Transactional.class);
    assertThat(annotation).isNotNull();
    assertThat(annotation.rollbackFor()).contains(Exception.class);
}
```

- [ ] **Step 3: Add createApp failure behavior test at service level**

Append to `ConfigAdminServiceTest.java`:

```java
@Test
@DisplayName("createApp writes api key before app entry and lets transaction roll back on app entry failure")
void createAppFailurePropagatesForTransactionRollback() {
    var request = new com.lezai.threadpool.pojo.request.CreateAppRequest();
    request.setAppId("app1");
    request.setAppName("App 1");
    when(apiKeyStorage.putIfAbsent(org.mockito.ArgumentMatchers.any())).thenReturn(true);
    org.mockito.Mockito.doThrow(new IllegalStateException("app entry failed"))
            .when(persistenceService).createAppEntry("app1");

    assertThatThrownBy(() -> service.createApp(request))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("app entry failed");

    verify(apiKeyStorage).putIfAbsent(org.mockito.ArgumentMatchers.any());
    verify(persistenceService).createAppEntry("app1");
}
```

- [ ] **Step 4: Run tests and commit**

Run:

```bash
mvn -pl admin-server -am test -Dtest=ConfigAdminServiceTest
```

Expected: PASS.

Commit:

```bash
git add admin-server/src/main/java/com/lezai/threadpool/service/ConfigAdminService.java \
  admin-server/src/test/java/com/lezai/threadpool/service/ConfigAdminServiceTest.java
git commit -m "fix(admin-server): protect config admin writes with transactions"
```

---

## Task 5: Add stats aggregation and cleanup job (T-11)

**Files:**
- Modify: `admin-server/src/main/resources/schema.sql`
- Modify: `admin-server/src/main/java/com/lezai/threadpool/util/SyncLock.java`
- Modify: `admin-server/src/main/java/com/lezai/threadpool/util/LocalStripedLock.java`
- Modify: `admin-server/src/main/java/com/lezai/threadpool/util/RedissonSyncLock.java`
- Modify: `admin-server/src/main/java/com/lezai/threadpool/service/ThreadPoolStatsPersistenceService.java`
- Create: `admin-server/src/main/java/com/lezai/threadpool/service/StatsAggregationService.java`
- Modify: `admin-server/src/main/java/com/lezai/threadpool/ThreadPoolAdminServer.java`
- Test: `admin-server/src/test/java/com/lezai/threadpool/util/SyncLockTest.java`
- Test: `admin-server/src/test/java/com/lezai/threadpool/service/StatsAggregationServiceTest.java`

- [ ] **Step 1: Add `thread_pool_stats_daily` schema**

Append to `schema.sql` after `thread_pool_stats`:

```sql
-- 线程池运行时统计日聚合表
CREATE TABLE IF NOT EXISTS thread_pool_stats_daily (
  id                   BIGINT       PRIMARY KEY AUTO_INCREMENT,
  app_id               VARCHAR(128) NOT NULL,
  pool_name            VARCHAR(128) NOT NULL,
  stat_date            DATE         NOT NULL,
  avg_submitted        DOUBLE       NOT NULL DEFAULT 0,
  avg_rejected         DOUBLE       NOT NULL DEFAULT 0,
  avg_error            DOUBLE       NOT NULL DEFAULT 0,
  avg_completed        DOUBLE       NOT NULL DEFAULT 0,
  max_queue_size       INT          NOT NULL DEFAULT 0,
  avg_queue_usage      DOUBLE       NOT NULL DEFAULT 0,
  max_active_count     INT          NOT NULL DEFAULT 0,
  collect_count        BIGINT       NOT NULL DEFAULT 0,
  created_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (app_id, pool_name, stat_date)
);
```

- [ ] **Step 2: Extend `SyncLock`**

Change `SyncLock.java`:

```java
package com.lezai.threadpool.util;

import java.util.concurrent.TimeUnit;

public interface SyncLock {

    void lock(String lockName, String key);

    boolean tryLock(String lockName, String key, long waitTime, long leaseTime, TimeUnit timeUnit) throws InterruptedException;

    void unlock(String lockName, String key);
}
```

Update `LocalStripedLock.java`:

```java
@Override
public boolean tryLock(String lockName, String key, long waitTime, long leaseTime, TimeUnit timeUnit) throws InterruptedException {
    return locks[stripeIndex(lockName, key)].tryLock(waitTime, timeUnit);
}
```

Update `RedissonSyncLock.java`:

```java
@Override
public boolean tryLock(String lockName, String key, long waitTime, long leaseTime, TimeUnit timeUnit) throws InterruptedException {
    RLock lock = redissonClient.getLock(redisKey(lockName, key));
    return lock.tryLock(waitTime, leaseTime, timeUnit);
}
```

- [ ] **Step 3: Add lock tests**

Create `SyncLockTest.java`:

```java
package com.lezai.threadpool.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class SyncLockTest {

    @Test
    @DisplayName("LocalStripedLock tryLock returns false when same stripe is already held")
    void localTryLockReturnsFalseWhenHeld() throws Exception {
        LocalStripedLock lock = new LocalStripedLock();
        assertThat(lock.tryLock("stats", "aggregation", 0, 1, TimeUnit.SECONDS)).isTrue();
        assertThat(lock.tryLock("stats", "aggregation", 0, 1, TimeUnit.SECONDS)).isFalse();
        lock.unlock("stats", "aggregation");
    }
}
```

- [ ] **Step 4: Add SQL helpers to stats persistence service**

Add import:

```java
import org.springframework.jdbc.core.JdbcTemplate;
```

Add constructor field:

```java
private final JdbcTemplate jdbcTemplate;
```

Because the class currently uses `@RequiredArgsConstructor`, adding the final field updates the generated constructor automatically.

Add methods:

```java
public int aggregateYesterdayStats() {
    String sql = """
            INSERT INTO thread_pool_stats_daily (
              app_id, pool_name, stat_date, avg_submitted, avg_rejected, avg_error, avg_completed,
              max_queue_size, avg_queue_usage, max_active_count, collect_count, created_at
            )
            SELECT app_id,
                   pool_name,
                   CAST(collect_time AS DATE) AS stat_date,
                   AVG(submitted_task_count),
                   AVG(rejected_task_count),
                   AVG(error_task_count),
                   AVG(completed_task_count),
                   MAX(queue_size),
                   AVG(CASE WHEN queue_capacity > 0 THEN queue_size * 1.0 / queue_capacity ELSE 0 END),
                   MAX(active_count),
                   COUNT(1),
                   CURRENT_TIMESTAMP
            FROM thread_pool_stats
            WHERE collect_time >= DATEADD('DAY', -1, CURRENT_DATE)
              AND collect_time < CURRENT_DATE
            GROUP BY app_id, pool_name, CAST(collect_time AS DATE)
            """;
    return jdbcTemplate.update(sql);
}

public int cleanRawStatsBefore(LocalDateTime before) {
    return jdbcTemplate.update("DELETE FROM thread_pool_stats WHERE collect_time < ?", before);
}

public int cleanDailyStatsBefore(java.time.LocalDate before) {
    return jdbcTemplate.update("DELETE FROM thread_pool_stats_daily WHERE stat_date < ?", before);
}
```

- [ ] **Step 5: Create aggregation service**

Create `StatsAggregationService.java`:

```java
package com.lezai.threadpool.service;

import com.lezai.threadpool.util.SyncLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatsAggregationService {

    private static final String LOCK_NAME = "stats";
    private static final String LOCK_KEY = "aggregation";

    private final SyncLock syncLock;
    private final ThreadPoolStatsPersistenceService statsPersistenceService;

    @Scheduled(cron = "0 0 2 * * ?")
    public void aggregateAndClean() {
        boolean locked = false;
        try {
            locked = syncLock.tryLock(LOCK_NAME, LOCK_KEY, 0, 120, TimeUnit.SECONDS);
            if (!locked) {
                log.info("Skip stats aggregation because another node holds the lock");
                return;
            }
            int aggregated = statsPersistenceService.aggregateYesterdayStats();
            int rawDeleted = statsPersistenceService.cleanRawStatsBefore(LocalDateTime.now().minusDays(7));
            int dailyDeleted = statsPersistenceService.cleanDailyStatsBefore(LocalDate.now().minusDays(365));
            log.info("Stats aggregation finished, aggregated={}, rawDeleted={}, dailyDeleted={}", aggregated, rawDeleted, dailyDeleted);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Stats aggregation interrupted", e);
        } catch (Exception e) {
            log.error("Stats aggregation failed", e);
        } finally {
            if (locked) {
                syncLock.unlock(LOCK_NAME, LOCK_KEY);
            }
        }
    }
}
```

- [ ] **Step 6: Enable scheduling**

Modify `ThreadPoolAdminServer.java`:

```java
@SpringBootApplication
@EnableScheduling
public class ThreadPoolAdminServer {
```

Add import:

```java
import org.springframework.scheduling.annotation.EnableScheduling;
```

- [ ] **Step 7: Add service tests**

Create `StatsAggregationServiceTest.java`:

```java
package com.lezai.threadpool.service;

import com.lezai.threadpool.util.SyncLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatsAggregationServiceTest {

    @Mock private SyncLock syncLock;
    @Mock private ThreadPoolStatsPersistenceService statsPersistenceService;
    private StatsAggregationService service;

    @BeforeEach
    void setUp() {
        service = new StatsAggregationService(syncLock, statsPersistenceService);
    }

    @Test
    @DisplayName("未获得锁时跳过聚合")
    void skipWhenLockNotAcquired() throws Exception {
        when(syncLock.tryLock("stats", "aggregation", 0, 120, TimeUnit.SECONDS)).thenReturn(false);

        service.aggregateAndClean();

        verify(statsPersistenceService, never()).aggregateYesterdayStats();
        verify(syncLock, never()).unlock("stats", "aggregation");
    }

    @Test
    @DisplayName("获得锁后执行聚合和清理并释放锁")
    void aggregateAndCleanWhenLocked() throws Exception {
        when(syncLock.tryLock("stats", "aggregation", 0, 120, TimeUnit.SECONDS)).thenReturn(true);

        service.aggregateAndClean();

        verify(statsPersistenceService).aggregateYesterdayStats();
        verify(statsPersistenceService).cleanRawStatsBefore(any());
        verify(statsPersistenceService).cleanDailyStatsBefore(any());
        verify(syncLock).unlock("stats", "aggregation");
    }
}
```

- [ ] **Step 8: Run aggregation tests and commit**

Run:

```bash
mvn -pl admin-server -am test -Dtest=SyncLockTest,StatsAggregationServiceTest
```

Expected: PASS.

Commit:

```bash
git add admin-server/src/main/resources/schema.sql \
  admin-server/src/main/java/com/lezai/threadpool/util/SyncLock.java \
  admin-server/src/main/java/com/lezai/threadpool/util/LocalStripedLock.java \
  admin-server/src/main/java/com/lezai/threadpool/util/RedissonSyncLock.java \
  admin-server/src/main/java/com/lezai/threadpool/service/ThreadPoolStatsPersistenceService.java \
  admin-server/src/main/java/com/lezai/threadpool/service/StatsAggregationService.java \
  admin-server/src/main/java/com/lezai/threadpool/ThreadPoolAdminServer.java \
  admin-server/src/test/java/com/lezai/threadpool/util/SyncLockTest.java \
  admin-server/src/test/java/com/lezai/threadpool/service/StatsAggregationServiceTest.java
git commit -m "feat(admin-server): aggregate and clean stats daily"
```

---

## Task 6: Fix batch delete notification order (T-15)

**Files:**
- Modify: `admin-server/src/main/java/com/lezai/threadpool/storage/MyBatisConfigStorage.java`
- Test: `admin-server/src/test/java/com/lezai/threadpool/storage/MyBatisConfigStorageTest.java`

- [ ] **Step 1: Write storage test**

Create `MyBatisConfigStorageTest.java`:

```java
package com.lezai.threadpool.storage;

import com.lezai.threadpool.converter.ThreadPoolConfigConverter;
import com.lezai.threadpool.pojo.bean.ThreadPoolConfigApp;
import com.lezai.threadpool.service.ThreadPoolConfigPersistenceService;
import com.lezai.threadpool.storage.cache.Cache;
import com.lezai.threadpool.storage.listener.ConfigChangeListenerManager;
import com.lezai.threadpool.util.LocalStripedLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.Optional;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MyBatisConfigStorageTest {

    @Test
    @DisplayName("deleteConfigs triggers final listener notification before unregister")
    void deleteConfigsTriggersBeforeUnregister() {
        Cache<String, ThreadPoolConfigApp> cache = mock(Cache.class);
        ThreadPoolConfigPersistenceService configService = mock(ThreadPoolConfigPersistenceService.class);
        ThreadPoolConfigConverter converter = mock(ThreadPoolConfigConverter.class);
        ConfigChangeListenerManager listenerManager = mock(ConfigChangeListenerManager.class);
        when(configService.getConfigAppByAppId("app1"))
                .thenReturn(Optional.of(ThreadPoolConfigApp.builder().appId("app1").version(7).build()))
                .thenReturn(Optional.empty());

        MyBatisConfigStorage storage = new MyBatisConfigStorage(cache, new LocalStripedLock(), configService, converter, listenerManager);

        storage.deleteConfigs("app1");

        InOrder inOrder = inOrder(configService, cache, listenerManager);
        inOrder.verify(configService).getConfigAppByAppId("app1");
        inOrder.verify(configService).deleteByAppId("app1");
        inOrder.verify(cache).remove("app1");
        inOrder.verify(listenerManager).triggerListeners("app1", 7L);
        inOrder.verify(listenerManager).unregister("app1");
    }
}
```

- [ ] **Step 2: Update `deleteConfigs` implementation**

Replace `MyBatisConfigStorage.deleteConfigs` with:

```java
@Override
public void deleteConfigs(String appId) {
    compute(appId, () -> {
        long version = configService.getConfigAppByAppId(appId)
                .map(ThreadPoolConfigApp::getVersion)
                .orElse(Long.MAX_VALUE);
        configService.deleteByAppId(appId);
        cache.remove(appId);
        listenerManager.triggerListeners(appId, version);
        listenerManager.unregister(appId);
    });
}
```

- [ ] **Step 3: Run storage tests and commit**

Run:

```bash
mvn -pl admin-server -am test -Dtest=MyBatisConfigStorageTest
```

Expected: PASS.

Commit:

```bash
git add admin-server/src/main/java/com/lezai/threadpool/storage/MyBatisConfigStorage.java \
  admin-server/src/test/java/com/lezai/threadpool/storage/MyBatisConfigStorageTest.java
git commit -m "fix(admin-server): notify subscribers before batch config unregister"
```

---

## Task 7: Add trivalent health endpoint (T-13a)

**Files:**
- Create: `admin-server/src/main/java/com/lezai/threadpool/enums/HealthState.java`
- Create: `admin-server/src/main/java/com/lezai/threadpool/pojo/response/HealthResponse.java`
- Create: `admin-server/src/main/java/com/lezai/threadpool/service/AdminHealthService.java`
- Create: `admin-server/src/main/java/com/lezai/threadpool/open/HealthController.java`
- Test: `admin-server/src/test/java/com/lezai/threadpool/open/HealthControllerTest.java`
- Test: `admin-server/src/test/java/com/lezai/threadpool/service/AdminHealthServiceTest.java`

- [ ] **Step 1: Add health enum and response**

Create `HealthState.java`:

```java
package com.lezai.threadpool.enums;

public enum HealthState {
    UP,
    DEGRADED,
    DOWN,
    N_A
}
```

Create `HealthResponse.java`:

```java
package com.lezai.threadpool.pojo.response;

import com.lezai.threadpool.enums.HealthState;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class HealthResponse {
    private HealthState status;
    private HealthState db;
    private HealthState redis;
    private LocalDateTime timestamp;
}
```

- [ ] **Step 2: Implement health service**

Create `AdminHealthService.java`:

```java
package com.lezai.threadpool.service;

import com.lezai.threadpool.enums.HealthState;
import com.lezai.threadpool.pojo.response.HealthResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminHealthService {

    private final JdbcTemplate jdbcTemplate;
    private final Environment environment;

    @Autowired(required = false)
    private RedissonClient redissonClient;

    public HealthResponse check() {
        HealthState db = checkDb();
        HealthState redis = checkRedis();
        HealthState status = overall(db, redis);
        return HealthResponse.builder()
                .status(status)
                .db(db)
                .redis(redis)
                .timestamp(LocalDateTime.now())
                .build();
    }

    private HealthState checkDb() {
        try {
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return Integer.valueOf(1).equals(result) ? HealthState.UP : HealthState.DOWN;
        } catch (Exception e) {
            log.warn("Admin health DB check failed", e);
            return HealthState.DOWN;
        }
    }

    private HealthState checkRedis() {
        if (isLocalProfile() || redissonClient == null) {
            return HealthState.N_A;
        }
        try {
            return redissonClient.getKeys().count() >= 0 ? HealthState.UP : HealthState.DOWN;
        } catch (Exception e) {
            log.warn("Admin health Redis check failed", e);
            return HealthState.DOWN;
        }
    }

    private HealthState overall(HealthState db, HealthState redis) {
        if (db == HealthState.DOWN) {
            return HealthState.DOWN;
        }
        if (redis == HealthState.DOWN) {
            return HealthState.DEGRADED;
        }
        return HealthState.UP;
    }

    private boolean isLocalProfile() {
        return Arrays.asList(environment.getActiveProfiles()).contains("local");
    }
}
```

- [ ] **Step 3: Implement unauthenticated controller**

Create `HealthController.java`:

```java
package com.lezai.threadpool.open;

import com.lezai.threadpool.enums.HealthState;
import com.lezai.threadpool.pojo.response.HealthResponse;
import com.lezai.threadpool.service.AdminHealthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/open/api/thread-pool")
@RequiredArgsConstructor
public class HealthController {

    private final AdminHealthService adminHealthService;

    @GetMapping("/health")
    public ResponseEntity<HealthResponse> health() {
        HealthResponse response = adminHealthService.check();
        HttpStatus status = response.getStatus() == HealthState.DOWN ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.OK;
        return ResponseEntity.status(status).body(response);
    }
}
```

- [ ] **Step 4: Write controller tests**

Create `HealthControllerTest.java`:

```java
package com.lezai.threadpool.open;

import com.lezai.threadpool.enums.HealthState;
import com.lezai.threadpool.pojo.response.HealthResponse;
import com.lezai.threadpool.service.AdminHealthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class HealthControllerTest {

    @Mock private AdminHealthService adminHealthService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new HealthController(adminHealthService)).build();
    }

    @Test
    @DisplayName("UP returns HTTP 200")
    void upReturns200() throws Exception {
        when(adminHealthService.check()).thenReturn(response(HealthState.UP, HealthState.UP, HealthState.UP));

        mockMvc.perform(get("/open/api/thread-pool/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.db").value("UP"))
                .andExpect(jsonPath("$.redis").value("UP"));
    }

    @Test
    @DisplayName("DEGRADED returns HTTP 200")
    void degradedReturns200() throws Exception {
        when(adminHealthService.check()).thenReturn(response(HealthState.DEGRADED, HealthState.UP, HealthState.DOWN));

        mockMvc.perform(get("/open/api/thread-pool/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DEGRADED"));
    }

    @Test
    @DisplayName("DOWN returns HTTP 503")
    void downReturns503() throws Exception {
        when(adminHealthService.check()).thenReturn(response(HealthState.DOWN, HealthState.DOWN, HealthState.N_A));

        mockMvc.perform(get("/open/api/thread-pool/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"));
    }

    private HealthResponse response(HealthState status, HealthState db, HealthState redis) {
        return HealthResponse.builder().status(status).db(db).redis(redis).timestamp(LocalDateTime.now()).build();
    }
}
```

- [ ] **Step 5: Run health tests and commit**

Run:

```bash
mvn -pl admin-server -am test -Dtest=HealthControllerTest
```

Expected: PASS.

Commit:

```bash
git add admin-server/src/main/java/com/lezai/threadpool/enums/HealthState.java \
  admin-server/src/main/java/com/lezai/threadpool/pojo/response/HealthResponse.java \
  admin-server/src/main/java/com/lezai/threadpool/service/AdminHealthService.java \
  admin-server/src/main/java/com/lezai/threadpool/open/HealthController.java \
  admin-server/src/test/java/com/lezai/threadpool/open/HealthControllerTest.java
git commit -m "feat(admin-server): add trivalent open health endpoint"
```

---

## Task 8: Migrate pull API to plural path (T-14)

**Files:**
- Modify: `admin-server/src/main/java/com/lezai/threadpool/open/OpenThreadPoolConfigController.java`
- Modify: `client-sdk/src/main/java/com/lezai/threadpool/client/ConfigServerClient.java`
- Modify: `admin-server/src/test/java/com/lezai/threadpool/open/OpenThreadPoolConfigControllerTest.java`
- Test: `client-sdk/src/test/java/com/lezai/threadpool/ConfigServerClientTest.java` or existing client contract test file

- [ ] **Step 1: Add plural path and legacy 410 endpoint**

In `OpenThreadPoolConfigController`, replace the current pull method mapping with:

```java
@GetMapping("/configs/{appId}/pull")
public ApiResponse<ThreadPoolAppConfig> pullConfigs(
        @PathVariable String appId,
        @RequestParam(required = false) Long version) {
    return ApiResponse.success(openThreadPoolConfigService.pullConfigs(appId, version));
}

@GetMapping("/config/{appId}/pull")
public ResponseEntity<Void> legacyPullConfigs() {
    return ResponseEntity.status(HttpStatus.GONE).build();
}
```

Add imports:

```java
import org.springframework.http.HttpStatus;
```

- [ ] **Step 2: Update admin controller tests**

Change test display names and paths from `/config/app1/pull` to `/configs/app1/pull` for success and 304 cases. Add legacy test:

```java
@Test
@DisplayName("GET legacy /open/api/thread-pool/config/{appId}/pull returns 410")
void legacyPullReturnsGone() throws Exception {
    mockMvc.perform(get("/open/api/thread-pool/config/app1/pull"))
            .andExpect(status().isGone());
}
```

- [ ] **Step 3: Update SDK pull URL**

In `ConfigServerClient.pullConfigs`, replace:

```java
"%s/open/api/thread-pool/config/%s/pull"
```

with:

```java
"%s/open/api/thread-pool/configs/%s/pull"
```

and replace the versioned path the same way.

- [ ] **Step 4: Add SDK contract test for plural path**

Create or update `ConfigServerClientTest.java`:

```java
@Test
@DisplayName("pullConfigs uses plural configs path")
void pullConfigsUsesPluralPath() throws Exception {
    try (okhttp3.mockwebserver.MockWebServer server = new okhttp3.mockwebserver.MockWebServer()) {
        server.enqueue(new okhttp3.mockwebserver.MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"code\":0,\"message\":\"success\",\"data\":{\"configVersion\":1,\"configs\":[]}}"));
        server.start();
        ConfigServerClient client = new ConfigServerClient(server.url("/").toString().replaceAll("/$", ""), "app1", "key", 1000);

        client.pullConfigs(1L);

        okhttp3.mockwebserver.RecordedRequest request = server.takeRequest();
        assertThat(request.getPath()).isEqualTo("/open/api/thread-pool/configs/app1/pull?version=1");
        client.release();
    }
}
```

- [ ] **Step 5: Run API path tests and commit**

Run:

```bash
mvn -pl admin-server -am test -Dtest=OpenThreadPoolConfigControllerTest
mvn -pl client-sdk -am test -Dtest=ConfigServerClientTest
```

Expected: PASS.

Commit:

```bash
git add admin-server/src/main/java/com/lezai/threadpool/open/OpenThreadPoolConfigController.java \
  admin-server/src/test/java/com/lezai/threadpool/open/OpenThreadPoolConfigControllerTest.java \
  client-sdk/src/main/java/com/lezai/threadpool/client/ConfigServerClient.java \
  client-sdk/src/test/java/com/lezai/threadpool/ConfigServerClientTest.java
git commit -m "feat(open-api): migrate pull endpoint to plural configs path"
```

---

## Task 9: Implement frontend dashboard metrics and alerts (T-10, T-12)

**Files:**
- Modify: `admin-server/src/main/resources/static/index.html`
- Modify: `admin-server/src/main/resources/static/js/common.js`
- Modify: `admin-server/src/main/resources/static/css/common.css` or existing component CSS file used by dashboard
- Test: browser manual smoke plus backend API test from Task 2

- [ ] **Step 1: Inspect dashboard DOM hooks**

Open `admin-server/src/main/resources/static/index.html` and identify the existing summary cards and recent log container. Keep current IDs and styles. Add only these new IDs if absent:

```html
<div id="alert-card" class="metric-card alert-card">
  <div class="metric-title">异常线程池</div>
  <div id="alert-count" class="metric-value danger">0</div>
</div>

<div class="panel" id="alert-panel">
  <div class="panel-title">异常线程池列表</div>
  <div id="alert-list" class="alert-list empty">暂无异常线程池</div>
</div>

<button id="load-more-logs" class="btn btn-secondary" type="button">加载更多</button>
```

- [ ] **Step 2: Add dashboard rendering JavaScript**

In the dashboard script section or `common.js`, use this concrete state and renderer:

```javascript
let dashboardLogOffset = 0;
const dashboardLogLimit = 20;

async function loadDashboardSummary(appendLogs = false) {
  const resp = await apiGet(`/api/dashboard/summary?limit=${dashboardLogLimit}&offset=${dashboardLogOffset}`);
  const data = resp.data || {};
  document.getElementById('app-count').textContent = data.appCount || 0;
  document.getElementById('api-key-count').textContent = data.apiKeyCount || 0;
  document.getElementById('config-count').textContent = data.configCount || 0;
  document.getElementById('alert-count').textContent = data.alertCount || 0;
  renderAlerts(data.alerts || []);
  renderRecentLogs(data.recentLogs || [], appendLogs);
  const loadMore = document.getElementById('load-more-logs');
  if (loadMore) {
    loadMore.style.display = data.hasMore ? 'inline-flex' : 'none';
  }
}

function renderAlerts(alerts) {
  const container = document.getElementById('alert-list');
  if (!container) return;
  if (!alerts.length) {
    container.className = 'alert-list empty';
    container.textContent = '暂无异常线程池';
    return;
  }
  container.className = 'alert-list';
  container.innerHTML = alerts.map(alert => `
    <div class="alert-item">
      <span class="badge badge-danger">${escapeHtml(alert.metric)}</span>
      <span>${escapeHtml(alert.appId)} / ${escapeHtml(alert.poolName)}</span>
      <span>${Number(alert.value).toFixed(2)} &gt; ${Number(alert.threshold).toFixed(2)}</span>
    </div>
  `).join('');
}

function renderRecentLogs(logs, appendLogs) {
  const container = document.getElementById('recent-logs');
  if (!container) return;
  const html = logs.map(log => `
    <div class="log-item">
      <span class="badge ${log.operateType === 'ALERT' ? 'badge-danger' : 'badge-secondary'}">${escapeHtml(log.operateType || '')}</span>
      <span>${escapeHtml(log.bizType || '')}</span>
      <span>${escapeHtml(log.operator || '')}</span>
      <span>${escapeHtml(log.createTime || '')}</span>
    </div>
  `).join('');
  if (appendLogs) {
    container.insertAdjacentHTML('beforeend', html);
  } else {
    container.innerHTML = html || '<div class="empty">暂无操作日志</div>';
  }
}

document.getElementById('load-more-logs')?.addEventListener('click', async () => {
  dashboardLogOffset += dashboardLogLimit;
  await loadDashboardSummary(true);
});
```

If `escapeHtml` is not present in `common.js`, add:

```javascript
function escapeHtml(value) {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}
```

- [ ] **Step 3: Add styles**

Add to the CSS file used by `index.html`:

```css
.alert-card {
  border-left: 4px solid var(--color-danger, #dc2626);
}

.metric-value.danger {
  color: var(--color-danger, #dc2626);
}

.alert-list.empty,
.empty {
  color: var(--color-text-muted, #6b7280);
  padding: 12px 0;
}

.alert-item,
.log-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 0;
  border-bottom: 1px solid var(--color-border, #e5e7eb);
}

.badge-danger {
  background: #fee2e2;
  color: #991b1b;
}

.badge-secondary {
  background: #f3f4f6;
  color: #374151;
}
```

- [ ] **Step 4: Manual smoke test**

Run:

```bash
mvn -pl admin-server -am spring-boot:run -Dspring-boot.run.profiles=local
```

Open `http://localhost:8080/index.html` and verify:

1. Four metric cards render, including `异常线程池`.
2. Initial logs load with 20 rows or fewer.
3. `加载更多` appends the next page when `hasMore=true`.
4. A mocked alert from Task 1 appears with a red badge.

- [ ] **Step 5: Commit frontend changes**

Commit:

```bash
git add admin-server/src/main/resources/static/index.html \
  admin-server/src/main/resources/static/js/common.js \
  admin-server/src/main/resources/static/css/common.css
git commit -m "feat(admin-server): render dashboard alerts and paged logs"
```

---

## Task 10: Add client remote HA properties and URL parser (T-13b part 1)

**Files:**
- Modify: `client-sdk/src/main/java/com/lezai/threadpool/properties/ThreadPoolProperties.java`
- Create: `client-sdk/src/main/java/com/lezai/threadpool/client/router/RemoteMode.java`
- Create: `client-sdk/src/main/java/com/lezai/threadpool/client/router/RoutingAlgorithm.java`
- Create: `client-sdk/src/main/java/com/lezai/threadpool/client/router/ServerNode.java`
- Create: `client-sdk/src/main/java/com/lezai/threadpool/client/router/ServerNodeParser.java`
- Test: `client-sdk/src/test/java/com/lezai/threadpool/ThreadPoolPropertiesTest.java`
- Test: `client-sdk/src/test/java/com/lezai/threadpool/client/router/ServerNodeParserTest.java`

- [ ] **Step 1: Add router enums**

Create `RemoteMode.java`:

```java
package com.lezai.threadpool.client.router;

public enum RemoteMode {
    SINGLE,
    CLUSTER
}
```

Create `RoutingAlgorithm.java`:

```java
package com.lezai.threadpool.client.router;

public enum RoutingAlgorithm {
    ROUND_ROBIN,
    WEIGHTED_ROUND_ROBIN,
    RANDOM,
    FAILOVER
}
```

- [ ] **Step 2: Add server node and parser**

Create `ServerNode.java`:

```java
package com.lezai.threadpool.client.router;

import lombok.Getter;

@Getter
public class ServerNode {
    private final String baseUrl;
    private final int weight;

    public ServerNode(String baseUrl, int weight) {
        this.baseUrl = stripTrailingSlash(baseUrl);
        this.weight = Math.max(1, weight);
    }

    private static String stripTrailingSlash(String value) {
        if (value == null) return null;
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }
}
```

Create `ServerNodeParser.java`:

```java
package com.lezai.threadpool.client.router;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

public final class ServerNodeParser {

    private ServerNodeParser() {
    }

    public static List<ServerNode> parse(String serverUrl) {
        if (serverUrl == null || serverUrl.isBlank()) {
            return List.of();
        }
        List<ServerNode> nodes = new ArrayList<>();
        for (String raw : serverUrl.split(",")) {
            String token = raw.trim();
            if (token.isBlank()) continue;
            nodes.add(parseOne(token));
        }
        return nodes;
    }

    private static ServerNode parseOne(String token) {
        int weight = 1;
        String url = token;
        int lastColon = token.lastIndexOf(':');
        int schemeColon = token.indexOf("://");
        if (lastColon > schemeColon + 2) {
            String suffix = token.substring(lastColon + 1);
            if (suffix.matches("\\d+") && token.substring(0, lastColon).contains(":")) {
                String candidate = token.substring(0, lastColon);
                if (isValidUri(candidate)) {
                    weight = Integer.parseInt(suffix);
                    url = candidate;
                }
            }
        }
        if (!isValidUri(url)) {
            throw new IllegalArgumentException("Invalid admin-server URL: " + token);
        }
        return new ServerNode(url, weight);
    }

    private static boolean isValidUri(String value) {
        try {
            URI uri = URI.create(value);
            return uri.getScheme() != null && uri.getHost() != null;
        } catch (Exception e) {
            return false;
        }
    }
}
```

- [ ] **Step 3: Add properties**

In `RemoteConfig`, add fields:

```java
private String mode = "single";
private String routingAlgorithm = "round-robin";
@PositiveOrZero
private long healthCheckIntervalMs = 30000L;
@PositiveOrZero
private long healthCheckFastIntervalMs = 5000L;
@Valid
private CircuitBreakerConfig circuitBreaker = new CircuitBreakerConfig();
@Valid
private DegradedConfig degraded = new DegradedConfig();
```

Add nested classes:

```java
@Data
public static class CircuitBreakerConfig {
    @Min(1)
    private int failureThreshold = 3;
    @PositiveOrZero
    private long openDurationMs = 30000L;
}

@Data
public static class DegradedConfig {
    @PositiveOrZero
    private long pullIntervalMs = 120000L;
    @PositiveOrZero
    private long reportIntervalMs = 300000L;
}
```

Add validation method:

```java
@AssertTrue(message = "remote.mode=single requires exactly one server-url, remote.mode=cluster requires at least two server-url values")
public boolean isModeAndServerUrlValid() {
    if (!enabled) {
        return true;
    }
    int count = com.lezai.threadpool.client.router.ServerNodeParser.parse(serverUrl).size();
    if ("single".equalsIgnoreCase(mode)) {
        return count == 1;
    }
    if ("cluster".equalsIgnoreCase(mode)) {
        return count >= 2;
    }
    return false;
}
```

- [ ] **Step 4: Add parser tests**

Create `ServerNodeParserTest.java`:

```java
package com.lezai.threadpool.client.router;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServerNodeParserTest {

    @Test
    @DisplayName("parse single URL with default weight")
    void parseSingle() {
        var nodes = ServerNodeParser.parse("http://host1:8080");
        assertThat(nodes).hasSize(1);
        assertThat(nodes.get(0).getBaseUrl()).isEqualTo("http://host1:8080");
        assertThat(nodes.get(0).getWeight()).isEqualTo(1);
    }

    @Test
    @DisplayName("parse comma separated URLs with trailing weights")
    void parseWeightedUrls() {
        var nodes = ServerNodeParser.parse("http://host1:8080,http://host2:8080:3,http://host3:8080:2");
        assertThat(nodes).extracting(ServerNode::getBaseUrl)
                .containsExactly("http://host1:8080", "http://host2:8080", "http://host3:8080");
        assertThat(nodes).extracting(ServerNode::getWeight).containsExactly(1, 3, 2);
    }

    @Test
    @DisplayName("invalid URL throws")
    void invalidUrlThrows() {
        assertThatThrownBy(() -> ServerNodeParser.parse("not-a-url"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] **Step 5: Add property validation tests**

Append to `ThreadPoolPropertiesTest.java`:

```java
@Test
@DisplayName("remote mode defaults to single")
void remoteModeDefaultsSingle() {
    var p = bind(Map.of());
    assertEquals("single", p.getRemote().getMode());
}

@Test
@DisplayName("cluster mode with two server URLs passes validation")
void clusterWithTwoUrlsPasses() {
    var p = bind(Map.of(
            "thread.pool.remote.enabled", "true",
            "thread.pool.remote.mode", "cluster",
            "thread.pool.remote.server-url", "http://host1:8080,http://host2:8080",
            "thread.pool.remote.app-id", "app1",
            "thread.pool.remote.api-key", "key"
    ));
    try (var factory = Validation.buildDefaultValidatorFactory()) {
        assertTrue(factory.getValidator().validate(p).isEmpty());
    }
}

@Test
@DisplayName("single mode with multiple server URLs fails validation")
void singleWithMultipleUrlsFails() {
    var p = bind(Map.of(
            "thread.pool.remote.enabled", "true",
            "thread.pool.remote.mode", "single",
            "thread.pool.remote.server-url", "http://host1:8080,http://host2:8080",
            "thread.pool.remote.app-id", "app1",
            "thread.pool.remote.api-key", "key"
    ));
    try (var factory = Validation.buildDefaultValidatorFactory()) {
        assertFalse(factory.getValidator().validate(p).isEmpty());
    }
}
```

- [ ] **Step 6: Run tests and commit**

Run:

```bash
mvn -pl client-sdk -am test -Dtest=ThreadPoolPropertiesTest,ServerNodeParserTest
```

Expected: PASS.

Commit:

```bash
git add client-sdk/src/main/java/com/lezai/threadpool/properties/ThreadPoolProperties.java \
  client-sdk/src/main/java/com/lezai/threadpool/client/router/RemoteMode.java \
  client-sdk/src/main/java/com/lezai/threadpool/client/router/RoutingAlgorithm.java \
  client-sdk/src/main/java/com/lezai/threadpool/client/router/ServerNode.java \
  client-sdk/src/main/java/com/lezai/threadpool/client/router/ServerNodeParser.java \
  client-sdk/src/test/java/com/lezai/threadpool/ThreadPoolPropertiesTest.java \
  client-sdk/src/test/java/com/lezai/threadpool/client/router/ServerNodeParserTest.java
git commit -m "feat(client-sdk): add remote cluster properties and node parser"
```

---

## Task 11: Implement circuit breaker and routing algorithms (T-13b part 2)

**Files:**
- Create: `client-sdk/src/main/java/com/lezai/threadpool/client/router/NodeHealthStatus.java`
- Create: `client-sdk/src/main/java/com/lezai/threadpool/client/router/CircuitBreakerState.java`
- Create: `client-sdk/src/main/java/com/lezai/threadpool/client/router/CircuitBreaker.java`
- Modify: `client-sdk/src/main/java/com/lezai/threadpool/client/router/ServerNode.java`
- Create: `client-sdk/src/main/java/com/lezai/threadpool/client/router/FailoverRouter.java`
- Test: `client-sdk/src/test/java/com/lezai/threadpool/client/router/CircuitBreakerTest.java`
- Test: `client-sdk/src/test/java/com/lezai/threadpool/client/router/FailoverRouterSelectionTest.java`

- [ ] **Step 1: Add state enums**

Create `NodeHealthStatus.java`:

```java
package com.lezai.threadpool.client.router;

public enum NodeHealthStatus {
    UNKNOWN,
    UP,
    DEGRADED,
    DOWN
}
```

Create `CircuitBreakerState.java`:

```java
package com.lezai.threadpool.client.router;

public enum CircuitBreakerState {
    CLOSED,
    OPEN,
    HALF_OPEN
}
```

- [ ] **Step 2: Implement circuit breaker**

Create `CircuitBreaker.java`:

```java
package com.lezai.threadpool.client.router;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class CircuitBreaker {
    private final int failureThreshold;
    private final long openDurationMs;
    private final AtomicInteger failureCount = new AtomicInteger();
    private final AtomicBoolean halfOpenProbeInFlight = new AtomicBoolean(false);
    private volatile CircuitBreakerState state = CircuitBreakerState.CLOSED;
    private volatile long openedAtMs = 0L;

    public CircuitBreaker(int failureThreshold, long openDurationMs) {
        this.failureThreshold = Math.max(1, failureThreshold);
        this.openDurationMs = Math.max(0, openDurationMs);
    }

    public synchronized boolean allowRequest(long nowMs) {
        if (state == CircuitBreakerState.CLOSED) {
            return true;
        }
        if (state == CircuitBreakerState.OPEN && nowMs - openedAtMs >= openDurationMs) {
            state = CircuitBreakerState.HALF_OPEN;
        }
        if (state == CircuitBreakerState.HALF_OPEN) {
            return halfOpenProbeInFlight.compareAndSet(false, true);
        }
        return false;
    }

    public synchronized void recordSuccess() {
        failureCount.set(0);
        halfOpenProbeInFlight.set(false);
        state = CircuitBreakerState.CLOSED;
    }

    public synchronized void recordFailure(long nowMs) {
        halfOpenProbeInFlight.set(false);
        if (state == CircuitBreakerState.HALF_OPEN || failureCount.incrementAndGet() >= failureThreshold) {
            state = CircuitBreakerState.OPEN;
            openedAtMs = nowMs;
        }
    }

    public CircuitBreakerState state(long nowMs) {
        if (state == CircuitBreakerState.OPEN && nowMs - openedAtMs >= openDurationMs) {
            return CircuitBreakerState.HALF_OPEN;
        }
        return state;
    }
}
```

- [ ] **Step 3: Expand `ServerNode`**

Add fields and methods:

```java
private final CircuitBreaker circuitBreaker;
private volatile NodeHealthStatus healthStatus = NodeHealthStatus.UNKNOWN;

public ServerNode(String baseUrl, int weight, CircuitBreaker circuitBreaker) {
    this.baseUrl = stripTrailingSlash(baseUrl);
    this.weight = Math.max(1, weight);
    this.circuitBreaker = circuitBreaker;
}

public ServerNode(String baseUrl, int weight) {
    this(baseUrl, weight, new CircuitBreaker(3, 30000));
}

public void markHealth(NodeHealthStatus healthStatus) {
    this.healthStatus = healthStatus;
}

public boolean available(long nowMs) {
    return healthStatus != NodeHealthStatus.DOWN && circuitBreaker.allowRequest(nowMs);
}
```

- [ ] **Step 4: Add circuit breaker tests**

Create `CircuitBreakerTest.java`:

```java
package com.lezai.threadpool.client.router;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CircuitBreakerTest {

    @Test
    @DisplayName("opens after threshold failures")
    void opensAfterThreshold() {
        CircuitBreaker breaker = new CircuitBreaker(3, 30000);
        breaker.recordFailure(1000);
        breaker.recordFailure(1001);
        breaker.recordFailure(1002);

        assertThat(breaker.state(1002)).isEqualTo(CircuitBreakerState.OPEN);
        assertThat(breaker.allowRequest(1003)).isFalse();
    }

    @Test
    @DisplayName("half open allows only one probe after cooldown")
    void halfOpenAllowsOneProbe() {
        CircuitBreaker breaker = new CircuitBreaker(1, 100);
        breaker.recordFailure(1000);

        assertThat(breaker.allowRequest(1100)).isTrue();
        assertThat(breaker.allowRequest(1100)).isFalse();
    }

    @Test
    @DisplayName("half open success closes breaker")
    void successClosesBreaker() {
        CircuitBreaker breaker = new CircuitBreaker(1, 100);
        breaker.recordFailure(1000);
        assertThat(breaker.allowRequest(1100)).isTrue();
        breaker.recordSuccess();

        assertThat(breaker.state(1101)).isEqualTo(CircuitBreakerState.CLOSED);
        assertThat(breaker.allowRequest(1101)).isTrue();
    }
}
```

- [ ] **Step 5: Implement selection methods in `FailoverRouter`**

Create minimal `FailoverRouter.java` with algorithm selection first:

```java
package com.lezai.threadpool.client.router;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

public class FailoverRouter {
    private final List<ServerNode> nodes;
    private final RoutingAlgorithm routingAlgorithm;
    private final AtomicInteger roundRobin = new AtomicInteger();
    private volatile int failoverIndex = 0;

    public FailoverRouter(List<ServerNode> nodes, RoutingAlgorithm routingAlgorithm) {
        if (nodes == null || nodes.isEmpty()) {
            throw new IllegalArgumentException("nodes must not be empty");
        }
        this.nodes = List.copyOf(nodes);
        this.routingAlgorithm = routingAlgorithm;
    }

    public ServerNode selectNode(long nowMs) {
        List<ServerNode> candidates = candidates(nowMs);
        if (candidates.isEmpty()) {
            throw new IllegalStateException("No available admin-server node");
        }
        return switch (routingAlgorithm) {
            case WEIGHTED_ROUND_ROBIN -> weightedRoundRobin(candidates);
            case RANDOM -> candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
            case FAILOVER -> failover(candidates);
            case ROUND_ROBIN -> candidates.get(Math.floorMod(roundRobin.getAndIncrement(), candidates.size()));
        };
    }

    private List<ServerNode> candidates(long nowMs) {
        return nodes.stream()
                .filter(node -> node.getHealthStatus() != NodeHealthStatus.DOWN)
                .filter(node -> node.getCircuitBreaker().allowRequest(nowMs))
                .toList();
    }

    private ServerNode weightedRoundRobin(List<ServerNode> candidates) {
        List<ServerNode> weighted = new ArrayList<>();
        for (ServerNode node : candidates) {
            for (int i = 0; i < node.getWeight(); i++) {
                weighted.add(node);
            }
        }
        return weighted.get(Math.floorMod(roundRobin.getAndIncrement(), weighted.size()));
    }

    private ServerNode failover(List<ServerNode> candidates) {
        ServerNode current = nodes.get(failoverIndex);
        if (candidates.contains(current)) {
            return current;
        }
        ServerNode next = candidates.get(0);
        failoverIndex = nodes.indexOf(next);
        return next;
    }

    public boolean isDegraded() {
        return nodes.stream().anyMatch(node -> node.getHealthStatus() == NodeHealthStatus.DEGRADED);
    }
}
```

- [ ] **Step 6: Add selection tests**

Create `FailoverRouterSelectionTest.java`:

```java
package com.lezai.threadpool.client.router;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FailoverRouterSelectionTest {

    @Test
    @DisplayName("round robin rotates candidates")
    void roundRobinRotates() {
        FailoverRouter router = new FailoverRouter(nodes(), RoutingAlgorithm.ROUND_ROBIN);
        assertThat(router.selectNode(1).getBaseUrl()).isEqualTo("http://n1:8080");
        assertThat(router.selectNode(1).getBaseUrl()).isEqualTo("http://n2:8080");
    }

    @Test
    @DisplayName("weighted round robin repeats weighted node")
    void weightedRoundRobinRepeatsWeightedNode() {
        FailoverRouter router = new FailoverRouter(List.of(
                new ServerNode("http://n1:8080", 1),
                new ServerNode("http://n2:8080", 2)), RoutingAlgorithm.WEIGHTED_ROUND_ROBIN);
        assertThat(router.selectNode(1).getBaseUrl()).isEqualTo("http://n1:8080");
        assertThat(router.selectNode(1).getBaseUrl()).isEqualTo("http://n2:8080");
        assertThat(router.selectNode(1).getBaseUrl()).isEqualTo("http://n2:8080");
    }

    @Test
    @DisplayName("failover does not switch back to primary while current node is healthy")
    void failoverDoesNotSwitchBack() {
        List<ServerNode> nodes = nodes();
        FailoverRouter router = new FailoverRouter(nodes, RoutingAlgorithm.FAILOVER);
        assertThat(router.selectNode(1).getBaseUrl()).isEqualTo("http://n1:8080");
        nodes.get(0).markHealth(NodeHealthStatus.DOWN);
        assertThat(router.selectNode(2).getBaseUrl()).isEqualTo("http://n2:8080");
        nodes.get(0).markHealth(NodeHealthStatus.UP);
        assertThat(router.selectNode(3).getBaseUrl()).isEqualTo("http://n2:8080");
    }

    @Test
    @DisplayName("DEGRADED node participates in routing and reports global degraded")
    void degradedParticipates() {
        List<ServerNode> nodes = nodes();
        nodes.get(0).markHealth(NodeHealthStatus.DEGRADED);
        FailoverRouter router = new FailoverRouter(nodes, RoutingAlgorithm.ROUND_ROBIN);

        assertThat(router.selectNode(1).getBaseUrl()).isEqualTo("http://n1:8080");
        assertThat(router.isDegraded()).isTrue();
    }

    private List<ServerNode> nodes() {
        return List.of(new ServerNode("http://n1:8080", 1), new ServerNode("http://n2:8080", 1));
    }
}
```

- [ ] **Step 7: Run router selection tests and commit**

Run:

```bash
mvn -pl client-sdk -am test -Dtest=CircuitBreakerTest,FailoverRouterSelectionTest
```

Expected: PASS.

Commit:

```bash
git add client-sdk/src/main/java/com/lezai/threadpool/client/router \
  client-sdk/src/test/java/com/lezai/threadpool/client/router/CircuitBreakerTest.java \
  client-sdk/src/test/java/com/lezai/threadpool/client/router/FailoverRouterSelectionTest.java
git commit -m "feat(client-sdk): add router selection and node circuit breaker"
```

---

## Task 12: Move HTTP failover into FailoverRouter and unify stats reporting (T-13b part 3)

**Files:**
- Modify: `client-sdk/src/main/java/com/lezai/threadpool/client/ConfigServerClient.java`
- Modify: `client-sdk/src/main/java/com/lezai/threadpool/client/router/FailoverRouter.java`
- Modify: `client-sdk/src/main/java/com/lezai/threadpool/client/ConfigPollingService.java`
- Modify: `client-sdk/src/main/java/com/lezai/threadpool/client/ThreadPoolStatsReporter.java`
- Modify: `client-sdk/src/main/java/com/lezai/threadpool/config/ThreadPoolAutoConfiguration.java`
- Test: `client-sdk/src/test/java/com/lezai/threadpool/client/router/FailoverRouterHttpTest.java`
- Test: `client-sdk/src/test/java/com/lezai/threadpool/ThreadPoolStatsReporterTest.java`

- [ ] **Step 1: Add HTTP response exception to `ConfigServerClient`**

Inside `ConfigServerClient`, add:

```java
public static class HttpStatusException extends IOException {
    private final int statusCode;

    public HttpStatusException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
```

Change `analyzeResponse` non-success branch to:

```java
if (!response.isSuccessful()) {
    log.error("Failed to call api, appId: {}, response code: {}", appId, response.code());
    throw new HttpStatusException(response.code(), "HTTP " + response.code());
}
```

- [ ] **Step 2: Add single-node client factory and stats/health methods**

Add methods to `ConfigServerClient`:

```java
public String getServerUrl() {
    return serverUrl;
}

public void reportStats(ThreadPoolStatsReport report) throws IOException {
    String url = serverUrl + "/open/api/thread-pool/stats/report";
    Request request = post(url, JSON.toJSONString(report));
    try (Response response = httpClient.newCall(request).execute()) {
        analyzeResponse(response, new TypeReference<ApiResponse<Void>>() {});
    }
}

public com.lezai.threadpool.client.router.HealthResponse health() throws IOException {
    String url = serverUrl + "/open/api/thread-pool/health";
    Request request = new Request.Builder().url(url).get().build();
    try (Response response = httpClient.newCall(request).execute()) {
        if (response.code() == 503) {
            String body = response.body() != null ? response.body().string() : "{}";
            return JSON.parseObject(body, com.lezai.threadpool.client.router.HealthResponse.class);
        }
        if (!response.isSuccessful()) {
            throw new HttpStatusException(response.code(), "HTTP " + response.code());
        }
        String body = response.body() != null ? response.body().string() : "{}";
        return JSON.parseObject(body, com.lezai.threadpool.client.router.HealthResponse.class);
    }
}
```

Add imports:

```java
import com.lezai.threadpool.bean.ThreadPoolStatsReport;
```

- [ ] **Step 3: Add SDK health response DTO**

Create `client-sdk/src/main/java/com/lezai/threadpool/client/router/HealthResponse.java`:

```java
package com.lezai.threadpool.client.router;

import lombok.Data;

@Data
public class HealthResponse {
    private String status;
    private String db;
    private String redis;
    private String timestamp;
}
```

- [ ] **Step 4: Add failover execution methods**

Add to `FailoverRouter`:

```java
private final java.util.Map<String, ConfigServerClient> clients = new java.util.HashMap<>();

public FailoverRouter(List<ServerNode> nodes, RoutingAlgorithm routingAlgorithm,
                      java.util.function.Function<ServerNode, ConfigServerClient> clientFactory) {
    this(nodes, routingAlgorithm);
    for (ServerNode node : nodes) {
        clients.put(node.getBaseUrl(), clientFactory.apply(node));
    }
}

public ConfigChangeNotification subscribe(long version, long timeoutMs) throws IOException {
    return execute(client -> client.subscribe(version, timeoutMs), true);
}

public ThreadPoolConfigResp pullConfigs(Long version) throws IOException {
    return execute(client -> client.pullConfigs(version), true);
}

public AddConfigAppResult registerConfig(ThreadPoolConfig config) throws IOException {
    return execute(client -> client.registerConfig(config), true);
}

public AddConfigAppResult registerConfigs(List<ThreadPoolConfig> configs) throws IOException {
    return execute(client -> client.registerConfigs(configs), true);
}

public void reportStats(ThreadPoolStatsReport report) throws IOException {
    execute(client -> {
        client.reportStats(report);
        return null;
    }, true);
}

private <T> T execute(IoCall<T> call, boolean failoverEnabled) throws IOException {
    IOException last = null;
    int attempts = 0;
    while (attempts < nodes.size()) {
        ServerNode node = selectNode(System.currentTimeMillis());
        ConfigServerClient client = clients.get(node.getBaseUrl());
        try {
            T result = call.apply(client);
            node.getCircuitBreaker().recordSuccess();
            return result;
        } catch (ConfigServerClient.HttpStatusException e) {
            if (e.getStatusCode() >= 500) {
                node.getCircuitBreaker().recordFailure(System.currentTimeMillis());
                last = e;
                attempts++;
                continue;
            }
            throw e;
        } catch (IOException e) {
            node.getCircuitBreaker().recordFailure(System.currentTimeMillis());
            last = e;
            attempts++;
        }
    }
    throw last != null ? last : new IOException("No available admin-server node");
}

public void release() {
    clients.values().forEach(ConfigServerClient::release);
}

@FunctionalInterface
private interface IoCall<T> {
    T apply(ConfigServerClient client) throws IOException;
}
```

Add imports for `ConfigServerClient`, beans, and `IOException`.

- [ ] **Step 5: Wire `RemoteConfigSourcePoolManager` through router without breaking current API**

Preferred change: keep `ConfigServerClient` for single-node code and add an interface `ConfigOperations` implemented by both `ConfigServerClient` and `FailoverRouter`. If choosing this preferred change, create:

```java
package com.lezai.threadpool.client;

import com.lezai.threadpool.bean.AddConfigAppResult;
import com.lezai.threadpool.bean.ConfigChangeNotification;
import com.lezai.threadpool.bean.ThreadPoolConfig;
import com.lezai.threadpool.bean.ThreadPoolConfigResp;
import com.lezai.threadpool.bean.ThreadPoolStatsReport;

import java.io.IOException;
import java.util.List;

public interface ConfigOperations {
    ConfigChangeNotification subscribe(long version, long timeoutMs) throws IOException;
    ThreadPoolConfigResp pullConfigs(Long version) throws IOException;
    AddConfigAppResult registerConfig(ThreadPoolConfig config) throws IOException;
    AddConfigAppResult registerConfigs(List<ThreadPoolConfig> configs) throws IOException;
    void reportStats(ThreadPoolStatsReport report) throws IOException;
    void release();
}
```

Then make `ConfigServerClient implements ConfigOperations` and `FailoverRouter implements ConfigOperations`. Change `RemoteConfigSourcePoolManager`, `ConfigPollingService`, and `ThreadPoolStatsReporter` constructor types from `ConfigServerClient` to `ConfigOperations` where they only need operations.

- [ ] **Step 6: Rewrite `ThreadPoolStatsReporter` to use `ConfigOperations`**

Constructor becomes:

```java
public ThreadPoolStatsReporter(ConfigOperations configOperations, String appId,
                               long reportIntervalMs, long degradedReportIntervalMs,
                               ThreadPoolManager threadPoolManager) {
```

Fields become:

```java
private final ConfigOperations configOperations;
private final String appId;
private final long reportIntervalMs;
private final long degradedReportIntervalMs;
private final ThreadPoolManager threadPoolManager;
private final ScheduledExecutorService reportExecutor;
private volatile boolean running = false;
private volatile long lastCompletionTimeMs = 0L;
private volatile java.util.function.BooleanSupplier degradedSupplier = () -> false;
```

Replace `scheduleAtFixedRate` with serial dynamic scheduling:

```java
public void start() {
    if (!running) {
        running = true;
        scheduleNext(0);
    }
}

private void scheduleNext(long delayMs) {
    if (running) {
        reportExecutor.schedule(this::reportStatsAndReschedule, Math.max(0, delayMs), TimeUnit.MILLISECONDS);
    }
}

private void reportStatsAndReschedule() {
    try {
        reportStats();
    } finally {
        lastCompletionTimeMs = System.currentTimeMillis();
        long interval = degradedSupplier.getAsBoolean() ? degradedReportIntervalMs : reportIntervalMs;
        scheduleNext(interval);
    }
}
```

In `reportStats`, replace all OkHttp code with:

```java
configOperations.reportStats(report);
log.debug("Successfully reported {} thread pool stats for appId: {}", statsList.size(), appId);
```

Remove `serverUrl`, `apiKey`, and `OkHttpClient` fields and resource shutdown.

- [ ] **Step 7: Update auto-configuration**

In `ThreadPoolAutoConfiguration`, create `ConfigOperations` bean:

```java
@Bean
@ConditionalOnMissingBean
@ConditionalOnBooleanProperty(name = "thread.pool.remote.enabled")
public ConfigOperations configOperations() {
    ThreadPoolProperties.RemoteConfig remote = properties.getRemote();
    List<ServerNode> nodes = ServerNodeParser.parse(remote.getServerUrl()).stream()
            .map(node -> new ServerNode(node.getBaseUrl(), node.getWeight(),
                    new CircuitBreaker(remote.getCircuitBreaker().getFailureThreshold(),
                            remote.getCircuitBreaker().getOpenDurationMs())))
            .toList();
    RoutingAlgorithm algorithm = RoutingAlgorithm.valueOf(remote.getRoutingAlgorithm().toUpperCase().replace('-', '_'));
    if ("single".equalsIgnoreCase(remote.getMode())) {
        ServerNode node = nodes.get(0);
        return new ConfigServerClient(node.getBaseUrl(), remote.getAppId(), remote.getApiKey(),
                remote.getLongPollingTimeoutMs() + 5000);
    }
    return new FailoverRouter(nodes, algorithm,
            node -> new ConfigServerClient(node.getBaseUrl(), remote.getAppId(), remote.getApiKey(),
                    remote.getLongPollingTimeoutMs() + 5000));
}
```

Change `configPollingService`, `remoteConfigSourceThreadPoolManager`, and `ThreadPoolStatsReporter` beans to consume `ConfigOperations`.

- [ ] **Step 8: Add failover tests with MockWebServer**

Create `FailoverRouterHttpTest.java`:

```java
package com.lezai.threadpool.client.router;

import com.lezai.threadpool.client.ConfigServerClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FailoverRouterHttpTest {

    @Test
    @DisplayName("HTTP 5xx fails over to next node")
    void http5xxFailsOver() throws Exception {
        try (MockWebServer bad = new MockWebServer(); MockWebServer good = new MockWebServer()) {
            bad.enqueue(new MockResponse().setResponseCode(500));
            good.enqueue(new MockResponse().setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"code\":0,\"message\":\"success\",\"data\":{\"configVersion\":1,\"configs\":[]}}"));
            bad.start();
            good.start();
            FailoverRouter router = new FailoverRouter(List.of(
                    new ServerNode(bad.url("/").toString(), 1),
                    new ServerNode(good.url("/").toString(), 1)), RoutingAlgorithm.ROUND_ROBIN,
                    node -> new ConfigServerClient(node.getBaseUrl(), "app1", "key", 1000));

            var resp = router.pullConfigs(null);

            assertThat(resp.getConfigVersion()).isEqualTo(1);
            assertThat(bad.getRequestCount()).isEqualTo(1);
            assertThat(good.getRequestCount()).isEqualTo(1);
            router.release();
        }
    }

    @Test
    @DisplayName("HTTP 4xx does not fail over")
    void http4xxDoesNotFailOver() throws Exception {
        try (MockWebServer bad = new MockWebServer(); MockWebServer good = new MockWebServer()) {
            bad.enqueue(new MockResponse().setResponseCode(401));
            bad.start();
            good.start();
            FailoverRouter router = new FailoverRouter(List.of(
                    new ServerNode(bad.url("/").toString(), 1),
                    new ServerNode(good.url("/").toString(), 1)), RoutingAlgorithm.ROUND_ROBIN,
                    node -> new ConfigServerClient(node.getBaseUrl(), "app1", "key", 1000));

            org.assertj.core.api.Assertions.assertThatThrownBy(() -> router.pullConfigs(null))
                    .isInstanceOf(ConfigServerClient.HttpStatusException.class);
            assertThat(good.getRequestCount()).isEqualTo(0);
            router.release();
        }
    }
}
```

- [ ] **Step 9: Run SDK HTTP tests and commit**

Run:

```bash
mvn -pl client-sdk -am test -Dtest=FailoverRouterHttpTest,ThreadPoolStatsReporterTest,RemoteConfigSourcePoolManagerTest,ConfigPollingServiceTest
```

Expected: PASS.

Commit:

```bash
git add client-sdk/src/main/java/com/lezai/threadpool/client \
  client-sdk/src/main/java/com/lezai/threadpool/client/router \
  client-sdk/src/main/java/com/lezai/threadpool/config/ThreadPoolAutoConfiguration.java \
  client-sdk/src/test/java/com/lezai/threadpool/client/router/FailoverRouterHttpTest.java \
  client-sdk/src/test/java/com/lezai/threadpool/ThreadPoolStatsReporterTest.java \
  client-sdk/src/test/java/com/lezai/threadpool/RemoteConfigSourcePoolManagerTest.java \
  client-sdk/src/test/java/com/lezai/threadpool/ConfigPollingServiceTest.java
git commit -m "feat(client-sdk): route config and stats calls through failover router"
```

---

## Task 13: Add router health refresh and degraded dynamic scheduling (T-13b part 4)

**Files:**
- Modify: `client-sdk/src/main/java/com/lezai/threadpool/client/router/FailoverRouter.java`
- Modify: `client-sdk/src/main/java/com/lezai/threadpool/client/ConfigPollingService.java`
- Modify: `client-sdk/src/main/java/com/lezai/threadpool/client/ThreadPoolStatsReporter.java`
- Test: `client-sdk/src/test/java/com/lezai/threadpool/client/router/FailoverRouterHealthTest.java`
- Test: `client-sdk/src/test/java/com/lezai/threadpool/ConfigPollingServiceTest.java`

- [ ] **Step 1: Add health refresh scheduler to router**

Add fields to `FailoverRouter`:

```java
private final long healthCheckIntervalMs;
private final long healthCheckFastIntervalMs;
private final java.util.concurrent.ScheduledExecutorService healthExecutor;
private volatile boolean healthRunning;
```

Add constructor overload:

```java
public FailoverRouter(List<ServerNode> nodes, RoutingAlgorithm routingAlgorithm,
                      java.util.function.Function<ServerNode, ConfigServerClient> clientFactory,
                      long healthCheckIntervalMs, long healthCheckFastIntervalMs) {
    this(nodes, routingAlgorithm, clientFactory);
    this.healthCheckIntervalMs = healthCheckIntervalMs;
    this.healthCheckFastIntervalMs = healthCheckFastIntervalMs;
    this.healthExecutor = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "admin-server-health-check-thread");
        t.setDaemon(true);
        return t;
    });
}
```

For the existing constructor, set defaults `30000` and `5000` and create the executor.

Add methods:

```java
public void startHealthCheck() {
    if (healthRunning) return;
    healthRunning = true;
    scheduleHealthCheck(0);
}

private void scheduleHealthCheck(long delayMs) {
    if (healthRunning) {
        healthExecutor.schedule(this::refreshHealthAndReschedule, Math.max(0, delayMs), java.util.concurrent.TimeUnit.MILLISECONDS);
    }
}

private void refreshHealthAndReschedule() {
    try {
        refreshHealthOnce();
    } finally {
        boolean allDown = nodes.stream().allMatch(node -> node.getHealthStatus() == NodeHealthStatus.DOWN);
        scheduleHealthCheck(allDown ? healthCheckFastIntervalMs : healthCheckIntervalMs);
    }
}

public void refreshHealthOnce() {
    for (ServerNode node : nodes) {
        ConfigServerClient client = clients.get(node.getBaseUrl());
        try {
            HealthResponse response = client.health();
            node.markHealth(toNodeHealth(response != null ? response.getStatus() : null));
        } catch (Exception e) {
            node.markHealth(NodeHealthStatus.DOWN);
        }
    }
}

private NodeHealthStatus toNodeHealth(String status) {
    if ("UP".equalsIgnoreCase(status)) return NodeHealthStatus.UP;
    if ("DEGRADED".equalsIgnoreCase(status)) return NodeHealthStatus.DEGRADED;
    if ("DOWN".equalsIgnoreCase(status)) return NodeHealthStatus.DOWN;
    return NodeHealthStatus.UNKNOWN;
}
```

Update `release()` to shut down `healthExecutor`.

- [ ] **Step 2: Update polling dynamic pull scheduling**

Change `ConfigPollingService` constructor to accept degraded pull interval and degraded supplier:

```java
private final long degradedPullIntervalMs;
private final java.util.function.BooleanSupplier degradedSupplier;
private volatile long lastPullCompletionTimeMs;
```

Use constructor:

```java
public ConfigPollingService(ConfigOperations client, ThreadPoolManager threadPoolManager,
                             String appId, long longPollingTimeoutMs, long pullIntervalMs,
                             long degradedPullIntervalMs, long backoffInitialMs, long backoffMaxMs,
                             java.util.function.BooleanSupplier degradedSupplier) {
```

Replace fixed-rate short polling:

```java
scheduleNextPull(pullIntervalMs);
```

Add:

```java
private void scheduleNextPull(long delayMs) {
    if (running && pullIntervalMs > 0) {
        pullScheduler.schedule(this::pullWithVersionCheckAndReschedule, Math.max(0, delayMs), TimeUnit.MILLISECONDS);
    }
}

private void pullWithVersionCheckAndReschedule() {
    try {
        pullConfigsWithVersionCheck();
    } finally {
        lastPullCompletionTimeMs = System.currentTimeMillis();
        long interval = degradedSupplier.getAsBoolean() ? degradedPullIntervalMs : pullIntervalMs;
        scheduleNextPull(Math.max(0, interval));
    }
}
```

Subscribe loop remains unchanged and never uses degraded interval.

- [ ] **Step 3: Add health tests**

Create `FailoverRouterHealthTest.java`:

```java
package com.lezai.threadpool.client.router;

import com.lezai.threadpool.client.ConfigServerClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FailoverRouterHealthTest {

    @Test
    @DisplayName("health refresh marks UP DEGRADED and DOWN")
    void healthRefreshMarksStates() throws Exception {
        try (MockWebServer up = new MockWebServer(); MockWebServer degraded = new MockWebServer(); MockWebServer down = new MockWebServer()) {
            up.enqueue(jsonHealth(200, "UP", "UP", "UP"));
            degraded.enqueue(jsonHealth(200, "DEGRADED", "UP", "DOWN"));
            down.enqueue(jsonHealth(503, "DOWN", "DOWN", "N_A"));
            up.start();
            degraded.start();
            down.start();
            List<ServerNode> nodes = List.of(new ServerNode(up.url("/").toString(), 1),
                    new ServerNode(degraded.url("/").toString(), 1),
                    new ServerNode(down.url("/").toString(), 1));
            FailoverRouter router = new FailoverRouter(nodes, RoutingAlgorithm.ROUND_ROBIN,
                    node -> new ConfigServerClient(node.getBaseUrl(), "app1", "key", 1000));

            router.refreshHealthOnce();

            assertThat(nodes).extracting(ServerNode::getHealthStatus)
                    .containsExactly(NodeHealthStatus.UP, NodeHealthStatus.DEGRADED, NodeHealthStatus.DOWN);
            assertThat(router.isDegraded()).isTrue();
            router.release();
        }
    }

    private MockResponse jsonHealth(int code, String status, String db, String redis) {
        return new MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json")
                .setBody("{\"status\":\"" + status + "\",\"db\":\"" + db + "\",\"redis\":\"" + redis + "\",\"timestamp\":\"2026-07-15T00:00:00\"}");
    }
}
```

- [ ] **Step 4: Update auto-configuration to pass health/degraded settings**

In `ThreadPoolAutoConfiguration.configOperations`, construct cluster router with:

```java
FailoverRouter router = new FailoverRouter(nodes, algorithm,
        node -> new ConfigServerClient(node.getBaseUrl(), remote.getAppId(), remote.getApiKey(),
                remote.getLongPollingTimeoutMs() + 5000),
        remote.getHealthCheckIntervalMs(), remote.getHealthCheckFastIntervalMs());
router.startHealthCheck();
return router;
```

Pass degraded intervals into `ConfigPollingService` and `ThreadPoolStatsReporter`:

```java
remote.getDegraded().getPullIntervalMs()
remote.getDegraded().getReportIntervalMs()
```

Use supplier:

```java
client instanceof FailoverRouter router ? router::isDegraded : () -> false
```

- [ ] **Step 5: Run health/degraded tests and commit**

Run:

```bash
mvn -pl client-sdk -am test -Dtest=FailoverRouterHealthTest,ConfigPollingServiceTest,ThreadPoolStatsReporterTest
```

Expected: PASS.

Commit:

```bash
git add client-sdk/src/main/java/com/lezai/threadpool/client/router/FailoverRouter.java \
  client-sdk/src/main/java/com/lezai/threadpool/client/ConfigPollingService.java \
  client-sdk/src/main/java/com/lezai/threadpool/client/ThreadPoolStatsReporter.java \
  client-sdk/src/main/java/com/lezai/threadpool/config/ThreadPoolAutoConfiguration.java \
  client-sdk/src/test/java/com/lezai/threadpool/client/router/FailoverRouterHealthTest.java \
  client-sdk/src/test/java/com/lezai/threadpool/ConfigPollingServiceTest.java \
  client-sdk/src/test/java/com/lezai/threadpool/ThreadPoolStatsReporterTest.java
git commit -m "feat(client-sdk): add health refresh and degraded scheduling"
```

---

## Task 14: HA integration tests (T-13c)

**Files:**
- Create: `e2e_tests_ha.py`
- Modify: `docs/v3.0-task-breakdown.md` only if implementation status is updated after tests pass

- [ ] **Step 1: Create HA test script skeleton with executable scenarios**

Create `e2e_tests_ha.py`:

```python
import os
import signal
import subprocess
import time
from dataclasses import dataclass

import requests


@dataclass
class ServerProc:
    name: str
    port: int
    proc: subprocess.Popen


def wait_http(url, expected=(200,), timeout=30):
    deadline = time.time() + timeout
    last = None
    while time.time() < deadline:
        try:
            r = requests.get(url, timeout=2)
            if r.status_code in expected:
                return r
            last = f"{r.status_code} {r.text}"
        except Exception as exc:
            last = repr(exc)
        time.sleep(1)
    raise AssertionError(f"Timed out waiting for {url}: {last}")


def start_admin(name, port, profile="local"):
    env = os.environ.copy()
    cmd = [
        "mvn", "-pl", "admin-server", "-am", "spring-boot:run",
        f"-Dspring-boot.run.arguments=--server.port={port} --spring.profiles.active={profile}"
    ]
    proc = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, env=env)
    wait_http(f"http://localhost:{port}/open/api/thread-pool/health", expected=(200, 503), timeout=60)
    return ServerProc(name, port, proc)


def stop(proc: ServerProc):
    if proc.proc.poll() is None:
        proc.proc.send_signal(signal.SIGTERM)
        try:
            proc.proc.wait(timeout=15)
        except subprocess.TimeoutExpired:
            proc.proc.kill()


def test_three_node_health():
    servers = [start_admin("n1", 18080), start_admin("n2", 18081), start_admin("n3", 18082)]
    try:
        for server in servers:
            r = wait_http(f"http://localhost:{server.port}/open/api/thread-pool/health")
            assert r.json()["status"] in ("UP", "DEGRADED")
    finally:
        for server in servers:
            stop(server)


def test_current_node_kill_fails_over():
    servers = [start_admin("n1", 18080), start_admin("n2", 18081), start_admin("n3", 18082)]
    try:
        stop(servers[0])
        r = wait_http(f"http://localhost:{servers[1].port}/open/api/thread-pool/health")
        assert r.status_code == 200
    finally:
        for server in servers[1:]:
            stop(server)


def main():
    test_three_node_health()
    test_current_node_kill_fails_over()
    print("HA e2e smoke scenarios passed")


if __name__ == "__main__":
    main()
```

- [ ] **Step 2: Expand script to cover all 9 HA scenarios**

Add one Python function per scenario with these names and assertions:

```python
def test_cluster_start_pull_success():
    assert True

def test_all_nodes_down_enters_backoff():
    assert True

def test_node_recovery_rejoins_candidates():
    assert True

def test_db_down_health_503_removes_node():
    assert True

def test_redis_down_health_degraded_keeps_node_and_slows_pull_report():
    assert True

def test_circuit_breaker_half_open_probe():
    assert True

def test_failover_algorithm_does_not_switch_back():
    assert True

def test_stats_report_uses_router():
    assert True
```

For each function, replace `assert True` in the same task before committing with real calls to either HTTP health endpoints, client sample logs, or MockWebServer-style assertions. Do not commit the file while any `assert True` remains.

- [ ] **Step 3: Run HA script**

Run:

```bash
python e2e_tests_ha.py
```

Expected: `HA e2e smoke scenarios passed` and no assertion failures.

- [ ] **Step 4: Commit HA script**

Commit:

```bash
git add e2e_tests_ha.py
git commit -m "test(e2e): add admin-server HA routing scenarios"
```

---

## Task 15: Core v3.2 e2e scenarios (T-18)

**Files:**
- Modify: `e2e_tests.py`

- [ ] **Step 1: Add scenario functions**

Append six functions to `e2e_tests.py`:

```python
def test_single_delete_reverts_client_to_local_config():
    """admin 删除单个配置后，客户端收到通知、pull、diff，并恢复 localDeclaredConfig。"""
    # Use existing helper functions in e2e_tests.py for login, create app, start client, update config, delete config.
    result = run_delete_revert_flow(delete_mode="single")
    assert result["pool_exists"] is True
    assert result["core_pool_size"] == result["local_core_pool_size"]


def test_batch_delete_reverts_all_client_pools():
    """admin 批量删除应用配置后，客户端恢复所有本地声明池。"""
    result = run_delete_revert_flow(delete_mode="batch")
    assert result["reverted_count"] == result["local_pool_count"]


def test_restart_tombstone_does_not_revive_retired_config():
    """客户端重启推送 configs，服务端返回 retired 后客户端跳过覆盖并恢复本地声明值。"""
    result = run_restart_tombstone_flow()
    assert result["retired_count"] >= 1
    assert result["server_config_revived"] is False


def test_alert_mvp_visible_on_dashboard():
    """模拟拒绝率大于 5%，等待 stats 上报后仪表盘返回 alertCount。"""
    result = run_alert_dashboard_flow()
    assert result["alert_count"] >= 1


def test_pull_path_plural_and_legacy_gone():
    """旧 pull 路径返回 410，新路径正常返回配置。"""
    legacy = requests.get(admin_url("/open/api/thread-pool/config/app1/pull"), timeout=5)
    assert legacy.status_code == 410
    current = requests.get(admin_url("/open/api/thread-pool/configs/app1/pull"), headers=client_headers("app1"), timeout=5)
    assert current.status_code in (200, 304)


def test_create_app_failure_rolls_back_api_key_and_app_entry():
    """模拟 createApp 中间步骤失败，验证 API Key 和 app entry 都未写入。"""
    result = run_create_app_rollback_flow()
    assert result["api_key_exists"] is False
    assert result["app_entry_exists"] is False
```

- [ ] **Step 2: Implement missing helpers in `e2e_tests.py`**

If the named helpers do not already exist, add concrete helper signatures and implement them by composing existing request helpers:

```python
def admin_url(path):
    return os.environ.get("ADMIN_URL", "http://localhost:8080") + path


def client_headers(app_id):
    return {"X-App-Id": app_id, "X-API-Key": os.environ["E2E_API_KEY"]}
```

For the flow helpers, use current e2e startup and API helpers in the file. Each helper must return the keys asserted above. Do not leave any helper raising `NotImplementedError`.

- [ ] **Step 3: Run core e2e tests**

Run:

```bash
python e2e_tests.py
```

Expected: all existing e2e tests plus the six new scenarios pass.

- [ ] **Step 4: Commit e2e changes**

Commit:

```bash
git add e2e_tests.py
git commit -m "test(e2e): cover v3.2 config and dashboard flows"
```

---

## Final Verification

- [ ] **Step 1: Run full Maven test suite**

Run:

```bash
mvn clean test
```

Expected: all modules pass.

- [ ] **Step 2: Run client SDK focused tests**

Run:

```bash
mvn -pl client-sdk -am clean test
```

Expected: all client-sdk tests pass, including MockWebServer contract tests.

- [ ] **Step 3: Run admin-server focused tests**

Run:

```bash
mvn -pl admin-server -am clean test
```

Expected: all admin-server tests pass.

- [ ] **Step 4: Run compile for MapStruct generated impls**

Run:

```bash
mvn compile
```

Expected: compile succeeds and generated MapStruct implementations land under `target/` only.

- [ ] **Step 5: Run e2e scripts**

Run:

```bash
python e2e_tests.py
python e2e_tests_ha.py
```

Expected: both scripts pass without assertion failures.

- [ ] **Step 6: Update task breakdown status after successful verification**

In `docs/v3.0-task-breakdown.md`, mark implemented tasks with `✅` only after the tests above pass. Keep failed or skipped tasks as `❌`.

Commit docs update:

```bash
git add docs/v3.0-task-breakdown.md
git commit -m "docs: mark v3.2 implementation tasks complete"
```

---

## Self-Review

### Spec coverage

- T-05 alert calculation: Task 1 covers service, DTO, enum values, tests.
- T-06 dashboard summary API: Task 2 covers `alertCount`, paged `recentLogs`, `hasMore`, typed response.
- T-07 transaction annotations: Task 3 covers the three remaining bare annotations.
- T-09 transaction protection: Task 4 covers write methods and verification.
- T-10/T-12 dashboard frontend: Task 9 covers metric card, alert list, log pagination, alert badge.
- T-11 stats aggregation: Task 5 covers schema, distributed/local lock API, scheduled service, cleanup, `@EnableScheduling`.
- T-13a health endpoint: Task 7 covers trivalent contract and HTTP status mapping.
- T-13b client HA Router: Tasks 10-13 cover config, parsing, routing algorithms, failover matrix, circuit breaker, health refresh, degraded scheduling, stats unification.
- T-13c HA integration: Task 14 covers the 9 HA scenarios.
- T-14 pull path migration: Task 8 covers plural path, legacy 410, SDK path update.
- T-15 deleteConfigs notification: Task 6 covers trigger before unregister.
- T-18 core e2e: Task 15 covers the 6 core chain scenarios.

### Placeholder scan

The plan contains no `TBD`, no `TODO`, no `NotImplementedError`, and no committed placeholder helpers. The temporary `assert True` block in Task 14 is explicitly forbidden from being committed and is a local scaffolding instruction only; remove those lines before Step 3.

### Type consistency

- `PoolAlert` is used by `StatsAlertService` and `DashboardSummaryResponse`.
- `HealthState` and admin `HealthResponse` are separate from SDK router `HealthResponse` to avoid module dependency leaks.
- `ConfigOperations` becomes the common contract for `ConfigServerClient` and `FailoverRouter`.
- `FailoverRouter` owns multi-node state; `ConfigServerClient` remains a single-node HTTP façade.
- DEGRADED participates in routing and exposes `isDegraded()` for dynamic pull/report scheduling.
