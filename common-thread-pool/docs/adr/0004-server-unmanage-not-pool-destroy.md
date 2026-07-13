# ADR 0004: 服务端删除配置 = 退管，而非销毁客户端池

## 状态
Accepted (2026-07-11)

## 背景
在 CS 模式下，管理员可在 admin-server 删除某条线程池配置。v1 代码中，
`MyBatisConfigStorage.deleteConfig` 仅执行 DB 软删除和缓存清除，**未触发
`ConfigChangeListenerManager.triggerListeners`**，导致长轮询客户端对删除
操作完全无感知。GAP-01 将此标记为 P0 级功能缺口。

修复 GAP-01 需要回答一个前置设计问题：**服务端删除配置后，客户端应该做什么？**

CONTEXT.md 已确立"可用性优先"原则（ADR-0001）：客户端本地配置是兜底地板，
服务端不可达时退化到本地仍可运行。但"服务端主动删除配置"的情况未被覆盖。

## 决策

**服务端删除配置表示"退管"（Server Unmanage），而非"销毁客户端池"。**

具体行为：
1. 服务端 `deleteConfig` 触发 `triggerListeners`，客户端通过长轮询收到变更通知
2. 客户端 pull 全量配置后 diff，发现池从服务端配置列表中消失
3. 客户端将该池的当前运行配置**回退到本地声明配置**（Local Declared Config），
   池继续运行，仅参数回归开发者最初声明的值
4. 服务端软删除记录充当 tombstone——客户端重启重推时，add 接口不复活已退管的配置
5. 退管不触发 `POOL_DESTROYED` 事件——池销毁仅在客户端主动调用 `removePool()` 或
   应用关闭时发生

**本地声明配置来源**：
- `thread.pool.pools[]` YAML → 回退到 YAML 声明值
- `@CreateThreadPool` 注解 → 回退到注解属性值
- `registerPool(config)` 编程式 → 回退到最初传入的 config
- `default-pool` → 回退到 `ThreadPoolInitializer.registerDefaultPool()` 的硬编码默认值

## 被否的替代

### 替代 A：优雅关闭池（`shutdown()`）
服务端删除 → 客户端 `shutdown()` 被退管的池，排队任务执行完毕后销毁。
**否决理由**：违背 ADR-0001 "可用性优先"原则。线程池是运行时基础设施，管理员
删除一条配置不应导致业务线程池消失。重启后池又会被本地声明重新创建（重推复活
被删配置），行为不一致。

### 替代 B：不做任何处理
服务端删除 → 客户端不做处理，池以当前配置继续运行。
**否决理由**：失去了"集中管理"的含义。运维删除了配置代表他不再信任服务端调的参数，
池应该回到本地声明值（"我不调了，你自己管"），而不是继续以服务端最后一次调的参数运行。

### 替代 C：删除即销毁，且禁止重启重推
服务端删除 → 客户端 `shutdownNow()` → 重启时从声明渠道中移除该池。
**否决理由**：声明渠道（`thread.pool.pools[]`、`@CreateThreadPool`）属于客户端
代码的一部分，服务端的操作不应篡改客户端的声明。这违反了配置所有权模型——
"客户端声明初始值"。

## 后果

### admin-server 侧
- `MyBatisConfigStorage.deleteConfig` / `deleteConfigs` 增加 `triggerListeners` 调用
- add 接口（`/open/api/.../configs/{appId}/add`）检测软删除 tombstone，返回
  "已退管"状态（409 Gone），客户端据此跳过重推
- 需与 client-sdk 联合发布（v2.0 采用联合发布策略）

### client-sdk 侧
- `DynamicThreadPoolWrapper` 新增 `localDeclaredConfig` 字段，在构造/注册时保存
- `ConfigPollingService.applyConfigs` 增加 diff 逻辑：服务端返回配置列表 vs
  客户端注册表 → 消失的池调用 `revertToLocalConfig()`
- 启动时重推阶段，若 add 接口返回 409 → 跳过该池的推送，保持本地声明配置运行
- `revertToLocalConfig()` 调用 `updateConfig(localDeclaredConfig)` 恢复参数

### 与 CONTEXT.md 的关系
本决策在 CONTEXT.md 中新增"配置退管（Server Unmanage）"术语章节，定义：
- 退管、本地声明配置、退管 tombstone、退管 ≠ 池销毁的语义边界

### 关联 ADR
- [ADR-0001](./0001-cs-mode-availability-first-config.md)：可用性优先原则——
  本决策是其自然延伸
- [ADR-0002](./0002-resizable-queue-capacity.md)：回退到本地声明配置时，
  若本地声明的队列类型不同，仅 `log.warn`（与 updateConfig 行为一致）
- [ADR-0003](./0003-apikey-no-rollback-differential-history.md)：API Key
  不支持回滚的决定与配置退管的决定相互独立——两者管控不同领域对象
