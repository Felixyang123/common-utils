# ADR-0006: FailoverRouter Decomposition with Strategy and Observer

> **Status**: Accepted — Implemented (2026-07-17, commit aa1f41e)
> **Date**: 2026-07-16
> **Context**: common-thread-pool CS 模式客户端 HA 路由，细化 [ADR-0005](./0005-client-side-admin-server-ha-routing.md)

## 背景

ADR-0005 引入的 `FailoverRouter` 把路由算法、后台健康检查调度、节点级熔断、故障切换执行四种职责全部塞进了一个 God Class。随着 v3.2 落地，扩展性问题暴露：新增任何一种路由算法或健康探测策略都要改 `FailoverRouter` 本体，组件无法独立测试、独立替换。

## 决策

把 `FailoverRouter` 拆成**三层解耦架构**：路由选择（Strategy）、健康探测（Observer 推送）、节点自治（节点内嵌熔断器），用组合代替继承，各组件通过回调接口单向通信。

### 组件职责

```text
ConfigPollingService / ThreadPoolStatsReporter
                ↓
          FailoverRouter (纯编排：select → node.execute → retry)
                ↓ selects from
          NodeManager (节点池 + 状态感知的候选列表缓存)
          ↙                    ↘
   HealthChecker              CircuitBreaker (per-node, 内嵌于 ServerNode)
   (独立后台线程,              (状态机 + 共享单线程调度器)
    探测后推送状态变更)
                ↑
          ServerNode (单节点门面)
    implements ConfigOperations
    ├── circuitBreaker.allowRequest()  ← 熔断检查
    └── client.xxx()                   ← 委托单节点 HTTP
```

### 关键决策

**1. 路由策略 — Strategy 模式**

```java
public interface RoutingStrategy {
    ServerNode select(List<ServerNode> candidates);
}
```

四种算法（round-robin / weighted-round-robin / random / failover）各自实现该接口，策略对象内部维护自身的状态（round-robin 的 `AtomicInteger`、failover 的 `failoverIndex`）。`FailoverRouter` 不再持有这些状态。

**2. 健康检查 — 推送模型（Observer）**

```java
public interface HealthChecker {
    void start();
    void stop();
}

public interface NodeStatusObserver {
    void onStatusChanged(ServerNode node, NodeHealthStatus newStatus);
}
```

- `HealthChecker` 是独立后台线程组件，按间隔轮询所有节点，**探测逻辑绕过熔断器**（恢复机制不能被自身熔断挡住）
- 状态变化时推送 `NodeStatusObserver.onStatusChanged()` 给 `NodeManager`
- `NodeManager` 实现 `NodeStatusObserver`，**增量维护** `cachedCandidates`，不做实时过滤
- `HealthChecker` 不持有状态本身，只是"变化检测器"

**3. 节点自治 — ServerNode 内嵌熔断器**

```java
public class ServerNode implements ConfigOperations {
    private final String baseUrl;
    private final int weight;
    private final ConfigServerClient client;
    private final CircuitBreaker circuitBreaker;
    private volatile NodeHealthStatus healthStatus = NodeHealthStatus.UNKNOWN;

    private <T> T execute(IoCall<T> call) throws IOException {
        long now = System.currentTimeMillis();
        if (!circuitBreaker.allowRequest(now)) throw new NodeUnavailableException(...);
        try { T r = call.apply(client); circuitBreaker.recordSuccess(); return r; }
        catch (IOException e) { circuitBreaker.recordFailure(now); throw e; }
    }
}
```

- 熔断检查 + HTTP 调用 + 失败记录封装在通用 `execute(IoCall)` 包装器里
- `NodeManager` 读取节点的 `CircuitBreaker` 状态用于候选过滤，**不反向引用 Node**

**4. 熔断状态推进 — 全局共享单线程调度器**

```java
public class CircuitBreakerScheduler {
    private static final ScheduledExecutorService EXECUTOR =
        Executors.newSingleThreadScheduledExecutor(...);
    public static ScheduledFuture<?> schedule(Runnable task, long delay, TimeUnit unit) { ... }
}
```

- `recordFailure()` 触发 OPEN 时，立刻安排一个 `openDurationMs` 后的定时任务
- 定时任务到期 → OPEN → HALF_OPEN → 回调 `CircuitBreakerStateListener`
- `NodeManager` 实现该监听器 → 加回候选池
- 单线程覆盖所有节点的冷却到期，避免每节点一线程的浪费

**5. 无循环依赖 — 外部注册观察者**

装配时序在组合根（`ThreadPoolAutoConfiguration`）里：

```java
List<ServerNode> nodes = createNodes(...);
HealthChecker healthChecker = new HttpHealthChecker(...);
NodeManager nodeManager = new DefaultNodeManager(nodes, healthChecker);

// 双向注册 —— 组件互不直接引用
nodes.forEach(n -> n.getCircuitBreaker().addStateListener(nodeManager));
healthChecker.registerObserver(nodeManager);
```

`ServerNode` 完全不感知 `NodeManager`，只通过回调接口通信。

### 候选列表过滤规则

| 节点状态 | `getCandidates()` | 说明 |
|---------|-------------------|------|
| 健康 `UP`/`DEGRADED` + 熔断 `CLOSED` | ✅ 在列表 | 正常路由 |
| 健康 + 熔断 `OPEN` | ❌ 移出 | 避免无意义的 `allowRequest()` 调用 |
| 健康 + 熔断 `HALF_OPEN` | ✅ 在列表 | 允许一次试探请求，成功后转 `CLOSED` |
| 健康 `DOWN` | ❌ 移出 | 健康检查判定不可用 |

### 接口/具体类划分

| 组件 | 类型 | 理由 |
|------|------|------|
| `RoutingStrategy` | 接口 | 核心扩展点 |
| `HealthChecker` | 接口 | 可替换探测实现 |
| `NodeManager` | 接口 | 测试 mock + 可替换 |
| `NodeStatusObserver` | 接口 | 健康状态推送契约 |
| `CircuitBreakerStateListener` | 接口 | 熔断状态变更契约 |
| `ServerNode` | 具体类 | 值对象，无多态需求 |
| `CircuitBreaker` | 具体类 | 状态机逻辑固定 |
| `FailoverRouter` | 具体类 | 唯一编排逻辑 |

### 重入候选池机制

```
OPEN ──(冷却到期, 全局调度器触发)──→ HALF_OPEN ──→ NodeManager.onBreakerStateChanged()
                                         ↓
                                   加回候选池 ← 允许 1 次试探
                                         ↓
                               ┌─ 试探成功 → CLOSED → 保持在候选池
                               └─ 试探失败 → OPEN  → 移出候选池, 重新计时
```

节点熔断后不会永久停摆，恢复完全由 `CircuitBreaker` 的冷却定时器自治驱动。

## 被拒绝的方案

### 方案 A' — 每节点独立调度线程

拒绝原因：每个 `CircuitBreaker` 创建独立 `ScheduledExecutorService`，节点多时浪费线程资源；共享单线程即可满足需求。

### 方案 B' — HealthChecker tick 驱动熔断转换

拒绝原因：把 `OPEN→HALF_OPEN` 转换延迟耦合到健康检查间隔，最大延迟为一个检查周期；且熔断器状态机不应依赖外部 tick 才能推进。

### 方案 C' — NodeManager 每轮 getCandidates() 实时过滤

拒绝原因：每次调用都重新检查所有节点的健康 + 熔断状态，本质是把编排逻辑从 `FailoverRouter` 搬到 `NodeManager`，没有真正解耦；推送 + 增量缓存避免了重复计算。

### 方案 D' — ServerNode 直接引用 NodeManager 回调

拒绝原因：造成 `ServerNode ↔ NodeManager` 循环依赖；改用 `CircuitBreakerStateListener` 挂在熔断器上，由 `NodeManager` 实现，解除循环。

## 后果

### 正向后果

- 路由算法、健康探测策略、熔断策略可独立扩展和替换
- 各组件可独立单元测试（mock `NodeManager` / `RoutingStrategy` / `HealthChecker`）
- `FailoverRouter` 瘦身为纯编排层，逻辑清晰
- 熔断恢复自治，不依赖外部调用节奏

### 代价与风险

- `CircuitBreaker` 需要增加状态监听器支持，比原来稍复杂
- 全局单线程调度器如果任务阻塞（理论上不会，因为任务只是状态转换），会延迟所有节点的冷却到期 —— 实际风险低，因为转换逻辑极轻
- 装配复杂度从"一个类搞定"变成"组合根里多行注册" —— 这是为解耦接受的代价

## 关联文档

- [ADR-0005: Client-side Admin Server HA Routing](./0005-client-side-admin-server-ha-routing.md)
- `../v3.0-task-breakdown.md`
