# ADR-0005: Client-side Admin Server HA Routing

> **Status**: Accepted  
> **Date**: 2026-07-15  
> **Context**: common-thread-pool CS 模式客户端连接 admin-server 的高可用设计

## 背景

当前 client-sdk 在 CS 模式下通过 `thread.pool.remote.server-url` 连接单个 admin-server。`ConfigServerClient` 持有单个 `serverUrl`，配置拉取、长轮询订阅、启动注册均面向同一个节点；`ThreadPoolStatsReporter` 也单独封装 HTTP 调用并持有同一个单地址配置。

admin-server 自身是无状态服务，理论上可以部署多个节点。但在客户端只配置单地址的情况下，单个 admin-server 节点宕机、网络不可达或服务端 5xx，会导致客户端无法自动切换到其他健康节点，只能等待当前节点恢复。这削弱了 CS 模式下配置推送、配置拉取和统计上报链路的可用性。

本决策目标是：在不引入服务发现、不改变 admin-server 无状态假设的前提下，让客户端支持 admin-server 集群连接、路由、故障切换、节点级熔断和降级保护。

## 决策

### 1. 显式区分 single / cluster 模式

新增客户端连接模式配置：

```yaml
thread:
  pool:
    remote:
      mode: single # single | cluster
      server-url: http://host1:8080,http://host2:8080:3,http://host3:8080:2
```

- `single`：单机连接模式，只允许配置一个 admin-server 地址。
- `cluster`：集群连接模式，必须配置两个及以上 admin-server 地址。
- `mode=single` 但配置多个地址，启动 fail-fast。
- `mode=cluster` 但配置少于两个地址，启动 fail-fast。
- 默认值为 `single`，保持现有行为兼容。

### 2. 多地址使用逗号分隔，权重跟随地址

多地址继续复用 `server-url`，使用逗号分隔：

```properties
thread.pool.remote.server-url=http://host1:8080,http://host2:8080:3,http://host3:8080:2
```

权重格式跟在地址后面，例如 `http://host2:8080:3` 表示该节点权重为 3。未显式配置权重时默认权重为 1。

### 3. 引入独立 `FailoverRouter`，保持 `ConfigServerClient` 单节点 HTTP 门面

不把多节点状态机直接塞进 `ConfigServerClient`。新增 `FailoverRouter` 层：

```text
ConfigPollingService / ThreadPoolStatsReporter
                ↓
          FailoverRouter
                ↓
  ConfigServerClient(node1/node2/node3...)
```

- `ConfigServerClient` 保持 dumb，只封装面向单个 admin-server 节点的 HTTP 调用。
- `FailoverRouter` 管理节点列表、路由算法、健康状态、故障切换、熔断与降级状态。
- `ThreadPoolStatsReporter` 不再单独封装 HTTP 上报调用；统计上报接口合并到 `ConfigServerClient`，统一使用同一套 HTTP 连接和 failover 机制。

### 4. cluster 模式支持常用路由算法

新增配置：

```properties
thread.pool.remote.routing-algorithm=round-robin
```

支持算法：

- `round-robin`
- `weighted-round-robin`
- `random`
- `failover` / primary-fallback

路由算法仅在 cluster 模式生效。默认值为 `round-robin`。

长轮询 subscribe 不要求粘性。admin-server 无状态，每次长轮询重新连接时都由 Router 重新路由。

`failover` 算法规则：

- 主节点可用时优先使用主节点。
- 主节点失败后切到后备节点。
- 主节点恢复后不主动回切，保持当前节点直到当前节点失败或被摘除，避免抖动。

### 5. failover 触发条件

触发节点切换和失败计数的条件：

- `IOException`：连接拒绝、连接超时、读写超时、DNS 失败等网络不可达场景。
- HTTP 5xx：服务端不可用或内部错误。

不触发 failover 的条件：

- HTTP 4xx：认证失败、路径错误、业务冲突等配置或业务问题。
- 业务响应码 `code != 0`：正常业务语义，不代表节点不可用。
- 长轮询 subscribe 的 30s 自然超时或空返回：这是正常长轮询语义，不触发 failover。

Router 对一次请求采取立即切换策略：当前节点失败后立即尝试下一个候选节点，不在 Router 内部做额外退避。所有可用节点都尝试一遍后仍失败，则向上抛出异常，由 `ConfigPollingService` 现有指数退避机制接管。

### 6. 健康检查使用三态

admin-server 新增健康检查端点：

```http
GET /open/api/thread-pool/health
```

响应状态为三态：

```json
{
  "status": "UP | DEGRADED | DOWN",
  "db": "UP | DOWN",
  "redis": "UP | DOWN | N/A",
  "timestamp": "..."
}
```

规则：

- DB DOWN → `status=DOWN`，HTTP 503。
- DB UP + Redis UP → `status=UP`，HTTP 200。
- DB UP + Redis DOWN → `status=DEGRADED`，HTTP 200。
- local profile 不依赖 Redis → `redis=N/A`，`status=UP`。

admin-server 数据访问使用 cache-aside，Redis DOWN 时可回源 DB，因此 Redis DOWN 不等同于节点不可用。

### 7. 健康状态刷新覆盖全部节点

Router 后台健康检查线程探测全部节点，而不是只探测 unhealthy 节点。

用途：

- 主动摘除 `DOWN` 节点。
- 发现已摘除节点恢复。
- 识别 `DEGRADED` 状态。
- Redis 恢复后自动解除客户端降级。

健康检查间隔：

```properties
thread.pool.remote.health-check-interval-ms=30000
thread.pool.remote.health-check-fast-interval-ms=5000
```

- 默认探测间隔 30s。
- 全部节点不可用时加速探测到 5s，尽快发现恢复节点。
- 节点一次探测成功即恢复，不要求连续多次成功。

全部节点 down 时，每次探测执行 `SELECT 1` 的开销可接受。

### 8. DEGRADED 同等参与路由，但触发客户端全局降级

`DEGRADED` 节点仍然与 `UP` 节点同等参与路由，不降低权重、不移出候选池。

原因：Redis 通常是 admin-server 集群共享依赖，`DEGRADED` 更像一个全局降压信号，而不是单节点路由质量信号。

当任一可用节点处于 `DEGRADED` 状态时，客户端进入全局降级模式：

- 短轮询 pull 使用降级间隔。
- 统计上报 reportStats 使用降级间隔。
- 长轮询 subscribe 不降级。

默认配置：

```properties
thread.pool.remote.degraded.pull-interval-ms=120000
thread.pool.remote.degraded.report-interval-ms=300000
```

Redis 恢复后，客户端自动恢复正常间隔。

动态调度不能简单使用 `schedule(currentInterval)`。需要保存最近一次完成时间，按剩余周期计算下一次延迟：

```text
delay = max(0, currentInterval - (now - lastCompletionTime))
```

采用完成时间口径，串行执行，避免同一任务并发重入。

### 9. 节点级熔断

熔断器与健康检查彼此独立。

- 健康检查负责周期性主动摘除或恢复节点。
- 熔断器负责请求过程中识别连续失败节点。

熔断粒度为节点级，不按接口级隔离。任一请求在某节点上触发连续失败计数，达到阈值后该节点整体熔断，所有接口都不再路由到它。

默认配置：

```properties
thread.pool.remote.circuit-breaker.failure-threshold=3
thread.pool.remote.circuit-breaker.open-duration-ms=30000
```

状态机：

- `CLOSED`：正常路由，失败计数达到阈值后进入 `OPEN`。
- `OPEN`：不再路由业务请求到该节点。
- `HALF_OPEN`：冷却时间到期后只放行 1 个试探请求。
  - 试探成功 → `CLOSED`，失败计数清零。
  - 试探失败 → 回到 `OPEN`，重新计冷却时间。

Router 选择候选节点时，过滤掉：

- 健康状态为 `DOWN` 的节点。
- 熔断状态为 `OPEN` 的节点。
- `HALF_OPEN` 且已有试探请求在途的节点。

Weighted Round Robin 在当前可用候选池中重新计算权重。

## 后果

### 正向后果

- 客户端可从单 admin-server 地址演进为多地址高可用连接。
- admin-server 继续保持无状态部署模型，不需要引入服务发现。
- 配置同步、启动注册、统计上报共享同一套 HTTP 与 failover 机制，避免重复实现。
- Redis 故障时通过 `DEGRADED` 全局降级减少 DB 压力，而不是误判为节点不可用。
- 熔断与健康检查分层，既能主动摘除故障节点，也能处理请求过程中暴露出的节点异常。

### 代价与风险

- client-sdk 复杂度显著上升，需要新增 Router、路由算法、节点状态、熔断器和动态调度逻辑。
- 多地址解析中地址与权重共用冒号，需要谨慎处理 URL scheme、端口和权重切分。
- 节点级熔断可能因为非核心接口失败导致配置拉取也暂时避开该节点；这是为了简化 HA 语义而接受的取舍。
- `DEGRADED` 节点同等参与路由，若 Redis 故障仅发生在单节点，仍会继续把流量路由到该节点；当前认为 Redis 多为共享依赖，此风险可接受。
- `ThreadPoolStatsReporter` 合并 HTTP 调用会触及现有构造链路与测试，需要完整回归。

## 被拒绝的方案

### 方案 A：把 failover 直接内置进 `ConfigServerClient`

拒绝原因：会让当前单节点 HTTP 门面承担节点池、路由、熔断、健康探测等复杂职责，边界不清晰，也不利于统计上报复用同一套机制。

### 方案 B：引入服务发现

拒绝原因：当前 admin-server 无状态且目标是轻量 HA；服务发现会增加外部依赖和部署复杂度，不符合 v3 范围。

### 方案 C：每次 poll/pull 前主动调用 health

拒绝原因：会增加请求链路开销，并把健康探测与业务请求强耦合。最终选择后台健康状态刷新线程统一维护节点状态。

### 方案 D：接口级熔断

拒绝原因：接口级状态更精细，但会让路由状态和错误处理复杂化。当前 HA 目标是节点可用性，因此采用节点级熔断。

## 关联文档

- `../admin-server-PRD.md`
- `../v3.0-task-breakdown.md`
- `../../CONTEXT.md`
