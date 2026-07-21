# ADR-0008: applyConfigs 不创建服务端下发但本地未声明的池

> **Status**: Accepted
> **Date**: 2026-07-20
> **Context**: client-sdk CS 模式 pull 路径（`ConfigPollingService.updatePools` → `applyConfigs`）

## 背景

CONTEXT.md §池的访问规则 已确立"先声明，后使用"：池只能由受认可的声明渠道产生（`@CreateThreadPool`、`pools[]`、`registerPool`、`default-pool`），访问未声明的池名抛 `PoolNotFoundException`，不静默兜底创建。"取数（访问）不再产生池"已作为缺陷移除。

但 CS 模式的 **pull 路径**存在同类缺陷的变体：`applyConfigs`（`DynamicThreadPoolWrapper` 调用 `ThreadPoolManager.upsertPool`）对服务端下发的池"有则更新、无则创建"——服务端列表里有、客户端本地未声明的池会被**直接创建**。

## 问题

这违反 declared-before-use，且与"一个 appId 多实例"的部署形态冲突：

- 客户端实例 A 声明 `pools=[orderPool, payPool]`，实例 B 只声明 `pools=[orderPool]`（同 appId，不同实例的本地声明可以不同）。
- A 启动时 `addConfigApp` 把 `payPool` 推到服务端 → 服务端 config 列表变成 `[orderPool, payPool]`。
- B pull 到 `[orderPool, payPool]` → `upsertPool(payPool)` **创建** `payPool`——即使 B 的本地 `pools[]` 从未声明过它。

这等于让服务端能"间接"给客户端塞一个未声明的池，绕过了声明渠道。它是 CONTEXT.md 已移除的"访问即产生池"在 pull 路径的残留。

## 决策

`applyConfigs` 对服务端有、本地未声明的池**不创建**，只记录 warn 并跳过。pull 后只用服务端值更新已声明的池。

配套：`addConfigApp` 因此**不广播变更通知**（不 trigger listener、不涨 version）。因为新池对其他客户端无意义——它们 pull 到也不会创建，trigger 只会无谓唤醒其他实例打断订阅等待。

## 被否的替代

- **保持 `upsertPool`（pull 即创建）**：让新池能自动传播到同 appId 其他实例。但这绕过了声明渠道，与 declared-before-use 直接冲突，且让"服务端 config 列表"具备了"池创建指令"的语义——违背服务端定位。否决。
- **`applyConfigs` 不创建 + `addConfigApp` 仍 trigger**：逻辑不自洽——trigger 唤醒其他实例 pull，pull 来的新池又不创建，trigger 纯属噪声。否决。

## 后果

- 新池要传播到一个 appId 的所有实例，必须靠**各实例在本地 `pools[]` 声明**（部署时所有实例都带上新池配置），而非靠服务端下发自动创建。
- `applyConfigs` 对未声明池 warn 跳过；`updatePools` 的"退管回退"逻辑（服务端没有的池 → revert 到 localDeclaredConfig）不受影响。
- `addConfigApp` 的 `ensureConfigApp` 无条件 `version+1` 改为不涨（重推已存在池是幂等操作，不应产生任何副作用，包括 version 涨）。
- 服务端 config 列表的语义明确为"声明池的集合 + 调优值"，不是"池的创建指令源"。详见 [CONTEXT.md §池的访问规则](../../CONTEXT.md)。
