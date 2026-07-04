# ADR 0003: API Key 不支持回滚，与线程池配置差异化历史存储

## 状态
Accepted (2026-07-03)

## 背景
admin-server 同时管理两类实体：ThreadPoolConfig 与 ApiKey。两者变更历史
原由同一个 ChangeLogEntry 承载，耦合了"版本快照"与"操作审计"两个领域。

解耦设计时需决定：API Key 是否像配置一样建独立快照表、支持对比与回滚。

## 决策
API Key 不建快照表、不支持回滚。其历史操作通过审计日志（operate_log）查看。
ThreadPoolConfig 建独立 config_history 快照表、支持对比与回滚。

## 理由
- ThreadPoolConfig 是业务调优对象，反复调整求最佳，回滚有实际价值。
- ApiKey 的轮换目的是安全重置凭证有效性、避免旧 key 泄露。回滚会让
  旧泄露凭证复活，违背轮换的安全目的。
- API Key 字段少（6 个），对比价值低于 11 字段的配置。
- 审计日志的 content 字段已存变更后完整 ApiKeyEntity JSON，按时间序列
  即为状态历史，满足"查看历史操作"需求，无需独立快照表。

## 后果
- API Key 侧零新建表、零新建 storage，仅删除旧历史类。
- GET /api/api-keys/{appId}/history 废弃，前端跳操作日志页预过滤。
- ChangeLogEntry / ChangeType / *HistoryStorage 一期可整体删除。
- 若未来确需 API Key 回滚（极不可能），需重新评估安全语义。
