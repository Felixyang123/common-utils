# ThreadPool Admin Server 产品规格文档（PRD）

> **版本**: v3.2
> **状态**: 活跃迭代（代码验证完成，高可用设计评审已落 ADR）
> **模块**: `common-thread-pool/admin-server` + `common-thread-pool/client-sdk`（联合发布）
> **关联模块**: `common-thread-pool/core`（指标模型）
> **技术栈**: Spring Boot 3.x / JDK 21 / MyBatis-Plus / JWT / BCrypt / Redisson
> **作者**: 产品通
> **日期**: 2026-07-15
> **变更说明**:
> - v1.0：纯逆向文档（2026-07-10）
> - v2.0：增加功能缺口分析、架构重构建议、v2 建设规格和演进路线图（2026-07-11 上午）
> - v2.1：grill-with-docs 评审后更新——新增依赖与前置条件章节、配置退管完整规格（ADR-0004）、告警 MVP 拆分、统计数据两级保留策略、发布里程碑定义、模块归属标注、指标字段映射（2026-07-11 下午）
> - v3.0：基于代码全量验证更新——8 项已完成（ARCH-02/08/09、GAP-03、SDK-01/02/03、Dashboard 基础摘要）、2 项部分完成（GAP-01 仅 deleteConfig 触发、ARCH-04 仅 4/7 方法正确）、SDK 侧配置退管已全链路落地；调整路线图和任务拆分（2026-07-15）
> - v3.1：修正 ARCH-05 误判（`@Bean` 方法同样由 Spring 容器管理并支持 AOP 代理）；F2-T-04/05 分布式锁改用 `RedissonSyncLock` 新增 `tryLock` API；F2-O-01 重写为 admin-server 高可用 + 客户端故障切换（非运维监控链路）；移除 F2-U-01（2026-07-15）
> - v3.2：grill-with-docs 评审 T-13 后落地 [ADR-0005](./adr/0005-client-side-admin-server-ha-routing.md)：显式 single/cluster 模式、FailoverRouter、路由算法、健康三态、节点级熔断、Redis DOWN 全局降级和 T-13a/b/c 拆分（2026-07-15）

---

## 目录

1. [问题陈述](#1-问题陈述)
2. [目标与非目标](#2-目标与非目标)
3. [用户角色](#3-用户角色)
4. [用户故事](#4-用户故事)
5. [系统现状（v1 已实现）](#5-系统现状v1-已实现)
6. [功能缺口分析](#6-功能缺口分析)
7. [架构问题诊断](#7-架构问题诊断)
8. [v2 建设规格](#8-v2-建设规格)
9. [非功能需求](#9-非功能需求)
10. [成功指标](#10-成功指标)
11. [技术架构概要](#11-技术架构概要)
12. [风险与缓解](#12-风险与缓解)
13. [演进路线图](#13-演进路线图)
14. [开放问题](#14-开放问题)
15. [附录](#15-附录)

---

## 1. 问题陈述

在微服务架构中，Java 应用普遍使用 `ThreadPoolExecutor` 处理异步任务。然而线程池参数（核心线程数、最大线程数、队列容量、拒绝策略等）通常硬编码在配置文件中，导致三大痛点：

1. **配置不透明**：运维和开发人员无法实时知道线上线程池的运行状态——有多少活跃线程、队列是否积压、任务是否被拒绝。
2. **变更成本高**：修改线程池参数需要改代码/配置 + 重新部署，无法在运行时动态调整，故障响应滞后。
3. **缺乏审计**：谁在什么时候改了哪个线程池的什么参数，没有记录，出问题难以追溯。

**ThreadPool Admin Server** 作为 `common-thread-pool` 生态的管理中枢，提供可视化的线程池配置管理、运行时状态监控、配置变更审计和实时推送能力，让开发者和运维人员能够集中管理所有接入应用的线程池。

---

## 2. 目标与非目标

### 2.1 目标（Goals）

| # | 目标 | 衡量标准 | v1 状态 |
|---|------|---------|---------|
| G1 | **集中配置管理**：管理员可通过 Web UI 对所有接入应用的线程池进行增删改查 | 支持至少 100 个应用、每个应用 50+ 线程池配置 | ✅ 已达成 |
| G2 | **运行时状态可见**：客户端 SDK 上报线程池运行时指标，管理后台可查询历史数据 | 统计上报延迟 < 5s，保留至少 7 天历史 | ⚠️ 部分达成（数据已收集入库，仪表盘基础摘要已实现，但未展示运行时指标和告警） |
| G3 | **配置变更追溯**：每次配置修改记录版本快照，支持版本对比和回滚 | 快照保留全量历史，回滚操作 < 1s | ✅ 已达成 |
| G4 | **实时配置推送**：管理后台修改配置后，客户端通过长轮询在 30s 内感知变更 | 长轮询超时 30s，补偿轮询兜底 | ✅ 已达成（单删除已触发通知，批量删除待修复） |
| G5 | **安全认证**：管理后台使用 JWT 认证，客户端使用 API Key 认证，操作记录审计日志 | 密码 BCrypt 哈希，API Key 仅创建时返回明文 | ✅ 已达成 |
| G6 | **多环境适配**：单机开发环境零依赖（H2 + 内存缓存），生产环境高可用（MySQL + Redis） | 通过 Spring Profile 一键切换 | ✅ 已达成 |
| G7 | **运行时告警**：当线程池出现异常时在仪表盘标记，并记录告警日志 | 拒绝率 > 5% 或队列使用率 > 80% 时仪表盘红色 Badge + operate_log 告警记录（v3.0 MVP）；完整告警系统（Webhook/分级/自定义阈值）v3.2 | ❌ 未达成（v3.0 待实现） |
| G8 | **多实例高可用**：admin-server 支持多实例部署，配置变更跨实例广播 | 多实例部署时长轮询订阅 100% 通知到达 | ❌ 未达成（v3.1 目标） |

### 2.2 非目标（Non-goals）

| # | 非目标 | 原因 |
|---|--------|------|
| NG1 | **不提供线程池自动调参/自适应策略** | 属于智能运维（AIOps）范畴，当前聚焦人工决策 + 手动调参 |
| NG2 | **不替代 APM 监控系统（如 Prometheus + Grafana）** | 统计上报聚焦线程池核心指标，不做全链路追踪或基础设施监控 |
| NG3 | **不支持多租户隔离** | 当前版本假设同一 admin-server 实例内所有应用属于同一信任域 |
| NG4 | **不提供邮件/短信/钉钉告警** | 告警通道依赖外部系统集成，v2 聚焦 Webhook 通知 |
| NG5 | **不支持 OAuth2 / LDAP / SSO 集成** | v2 使用内置用户名密码认证，企业 SSO 为后续迭代方向 |
| NG6 | **API Key 不支持细粒度权限（RBAC for clients）** | 当前一个 API Key 拥有对应 appId 的全部操作权限，不区分读写 |

---

## 3. 用户角色

| 角色 | 描述 | 核心场景 |
|------|------|---------|
| **超级管理员（SUPER_ADMIN）** | 系统初始管理员，拥有全部权限 | 创建/管理其他管理员账号、管理 API Key、配置线程池、查看审计日志 |
| **普通管理员（ADMIN）** | 被 SUPER_ADMIN 创建的日常运维人员 | 管理 API Key、配置线程池、查看审计日志（不能管理其他管理员） |
| **客户端 SDK** | 接入 `common-thread-pool` 的 Java 应用 | 上报线程池配置和运行时统计、拉取最新配置、订阅配置变更 |

---

## 4. 用户故事

### 4.1 仪表盘（Dashboard）

> **US-D1**：作为**管理员**，我希望登录后看到一个汇总仪表盘，一眼看到当前管理的应用数量、API Key 数量和线程池配置总数，以便快速掌握系统整体状况。

> **US-D2**：作为**管理员**，我希望仪表盘展示最近的操作记录（谁在什么时候做了什么操作），以便追踪最近的变更活动。

> **US-D3**：作为**管理员**，我希望仪表盘展示实时运行指标摘要（有多少线程池队列积压、有多少拒绝任务），以便第一时间发现异常。**（v2 新增）**

> **US-D4**：作为**管理员**，我希望仪表盘展示近 7 天的配置变更趋势图，以便了解系统活跃度。**（v2 新增）**

### 4.2 线程池配置管理

> **US-C1**：作为**管理员**，我希望按应用（appId）查看该应用下所有的线程池配置列表，包括池名称、核心/最大线程数、队列类型和容量、拒绝策略，以便了解每个应用的线程池拓扑。

> **US-C2**：作为**管理员**，我希望创建新的线程池配置，设置池名称、核心/最大线程数、队列类型、队列容量、拒绝策略、存活时间、线程名前缀等参数，以便为新业务配置合理的线程池。

> **US-C3**：作为**管理员**，我希望编辑已有的线程池配置（如调整核心线程数或队列容量），修改后客户端能自动感知变更，以便在流量变化时动态调优。

> **US-C4**：作为**管理员**，我希望删除不再需要的线程池配置，以便清理历史遗留的无用配置。

> **US-C5**：作为**管理员**，我希望查看某个线程池配置的历史版本列表，选择任意两个版本进行字段级对比（差异高亮），以便理解配置的演变过程。

> **US-C6**：作为**管理员**，我希望将某个线程池配置回滚到任意历史版本，以便在错误修改后快速恢复。

> **US-C7**：作为**管理员**，我希望批量选择多个线程池，统一修改同一参数（如队列容量），以便高效完成批量调优。**（v2 新增）**

> **US-C8**：作为**管理员**，我希望使用预设模板（"高吞吐"/"低延迟"/"IO 密集型"）一键应用配置，以便快速配置常见场景。**（v2 新增）**

### 4.3 API Key 管理

> **US-A1**：作为**管理员**，我希望创建一个新的 API Key（绑定 appId、应用名称、过期时间），创建成功后仅展示一次明文 Key，以便安全地分发给接入方。

> **US-A2**：作为**管理员**，我希望查看所有 API Key 的列表（脱敏显示，不包含哈希值），支持分页和搜索，以便管理大量接入应用。

> **US-A3**：作为**管理员**，我希望更新 API Key 的元数据（应用名称、描述、启用/禁用状态、过期时间），以便在应用信息变更或需要临时禁用时快速操作。

> **US-A4**：作为**管理员**，我希望重新生成某个 API Key（返回新明文），旧 Key 立即失效，以便在密钥泄露时快速响应。

> **US-A5**：作为**管理员**，我希望在 API Key 即将过期前 7 天看到警告标识，以便提前续期避免客户端断连。**（v2 新增）**

### 4.4 操作审计日志

> **US-L1**：作为**管理员**，我希望查看全量操作日志（按业务类型、操作类型、操作人、业务 ID 过滤），支持分页，以便在安全审计或故障排查时追溯操作历史。

> **US-L2**：作为**管理员**，我希望每条操作日志记录操作前后的内容快照（JSON 格式），以便精确了解变更细节。

> **US-L3**：作为**管理员**，我希望导出操作日志为 CSV/Excel 文件，以便提交合规审计报告。**（v2 新增）**

### 4.5 管理员账号管理

> **US-U1**：作为**超级管理员**，我希望创建新的管理员账号（设置用户名、密码、昵称、角色），以便授权其他运维人员使用管理后台。

> **US-U2**：作为**超级管理员**，我希望查看所有管理员列表、编辑管理员信息、禁用或删除管理员，以便管理团队权限。

> **US-U3**：作为**任意管理员**，我希望修改自己的密码和昵称，以便保障个人账户安全。

> **US-U4**：作为**系统**，默认 `admin` 用户不可删除、不可禁用、角色不可更改，管理员不能删除/禁用自己，以防止误操作导致无法登录。

> **US-U5**：作为**管理员**，我希望连续登录失败 5 次后账号被锁定 15 分钟，以防止暴力破解。**（v2 新增）**

### 4.6 客户端 SDK 接入（Open API）

> **US-O1**：作为**客户端应用**，我希望在启动时将自己的线程池配置上报到 admin-server（已存在的配置自动跳过），以便服务端有全量的配置基线。

> **US-O2**：作为**客户端应用**，我希望通过短轮询接口拉取服务端的最新配置（支持版本号对比，未变更返回 304），以便在主动检查时获取最新配置。

> **US-O3**：作为**客户端应用**，我希望通过长轮询接口订阅配置变更，当管理员修改配置后在 30s 内收到变更通知（仅含 appId + 版本号），以便实时热更新线程池参数。

> **US-O4**：作为**客户端应用**，我希望定期上报线程池运行时统计（活跃线程数、队列大小、已完成任务数、错误数、拒绝数等），以便管理后台展示运行状态。

> **US-O5**：作为**客户端应用**，我希望管理员删除配置后也能通过长轮询收到通知，以便客户端同步移除已删除的线程池。**（v2 新增）**

### 4.7 认证与安全

> **US-S1**：作为**管理员**，我希望通过用户名密码登录管理后台（JWT 8 小时过期），在过期前 30 分钟内操作自动滑动续期，修改密码后旧 Token 立即失效，以兼顾安全性和使用便利性。

> **US-S2**：作为**客户端应用**，我希望通过 `X-App-Id` + `X-API-Key` Header 认证调用 Open API，密钥哈希存储（BCrypt），支持启用/禁用和过期控制。

### 4.8 监控与告警

> **US-M1**：作为**管理员**，我希望在监控页面查看某个线程池的实时运行指标折线图（活跃线程、队列大小、池大小），以便了解线程池负载趋势。**（v2 新增）**

> **US-M2**：作为**管理员**，我希望当某个线程池拒绝率超过阈值时收到告警通知（仪表盘红色标记 + Webhook），以便及时扩容或调整参数。**（v2 新增）**

---

## 5. 系统现状（v1 已实现）

> 本章节描述代码中**已实际实现**的功能，不再使用"P0 必须实现"措辞。

### 5.1 仪表盘

| 功能 | 实现状态 | 说明 |
|------|---------|------|
| 三统计卡片（应用数/Key 数/配置数） | ✅ | `GET /api/dashboard/summary` 聚合查询 |
| 最近 5 条操作记录 | ✅ | `OperateLogService.getRecentLogs(5)` |
| 数据更新时间戳 | ✅ | 前端渲染时显示 |
| 实时运行指标摘要 | ❌ | 数据在 `thread_pool_stats` 表但仪表盘未展示 |
| 配置变更趋势图 | ❌ | 未实现 |
| 异常线程池提醒 | ❌ | 未实现 |
| 分页加载更多日志 | ❌ | 固定 5 条，无分页 |

> **v3.0 验证**：仪表盘基础摘要已实现（appCount/apiKeyCount/configCount/recentLogs），运行指标和告警功能尚未实现。

### 5.2 线程池配置管理

| 功能 | 实现状态 | 说明 |
|------|---------|------|
| 应用选择器（appId + 版本号 + 池数量） | ✅ | `GET /api/thread-pool/configs` 返回 `AppConfigSummary` 列表 |
| 配置列表表格 | ✅ | 展示池名称、核心/最大线程数、队列类型(容量)、拒绝策略、存活时间 |
| 新建/编辑线程池弹窗 | ✅ | 6 种队列类型 + 5 种拒绝策略 + 全部参数可配置 |
| 参数校验 | ✅ | 前端即时校验 + 后端 `@Valid` |
| 删除单个线程池（软删除 + 通知客户端） | ✅ | `@TableLogic` 逻辑删除 + 二次确认 + `triggerListeners` 触发客户端通知 |
| 删除应用全部配置（通知缺失） | ⚠️ | `deleteConfigs(appId)` 调用 `unregister(appId)` 移除监听器但未 `triggerListeners`，客户端仅靠超时感知 |
| 回收站（已删除配置查看与恢复） | ✅ | `GET /api/thread-pool/configs/deleted` + `PUT .../restore`，返回 DTO |
| 版本快照（每次修改自动记录） | ✅ | `config_history` 表，版本号按 (appId, poolName) 递增 |
| 配置历史页 + 版本对比 | ✅ | `config-diff.html` 双栏 11 字段对比 + 差异高亮 |
| 配置回滚 | ✅ | 回滚创建新快照 + 触发客户端通知 |
| 应用管理（新建/删除） | ✅ | 新建应用同时创建 API Key，删除级联清理 |
| 批量编辑 | ❌ | 未实现 |
| 配置模板 | ❌ | 未实现 |
| 配置导入/导出 | ❌ | 未实现 |

### 5.3 API Key 管理

| 功能 | 实现状态 | 说明 |
|------|---------|------|
| 创建 API Key（32 位随机 + BCrypt 哈希） | ✅ | `ApiKeyUtils.generateRandomApiKey()` + `SecureRandom` |
| 明文 Key 仅展示一次 | ✅ | 创建/重新生成时返回，查询/列表接口脱敏 |
| 分页列表 | ✅ | `GET /api/api-keys?page=&pageSize=` |
| 更新元数据 | ✅ | 应用名称、描述、启用/禁用、过期时间 |
| 重新生成（旧 Key 立即失效） | ✅ | `POST /api/api-keys/{appId}/regenerate` |
| 删除 | ✅ | 逻辑删除 |
| 三级认证校验 | ✅ | 存在性 → enabled → 未过期 → BCrypt 匹配 |
| 搜索（按 appId） | ❌ | 前端无搜索框 |
| IP 白名单 | ❌ | 未实现 |
| 读写权限分离 | ❌ | 未实现 |
| 过期预警 | ❌ | 未实现 |

### 5.4 操作审计日志

| 功能 | 实现状态 | 说明 |
|------|---------|------|
| 自动记录操作日志 | ✅ | `@Async("historyRecordExecutor")` 异步写入 |
| 多条件过滤查询 | ✅ | bizType / operateType / operator / bizId |
| 分页（pageSize 上限 100） | ✅ | `GET /api/operate-logs/list` |
| JSON 内容格式化展示 | ✅ | `showContent()` 尝试 `JSON.parse` |
| 操作类型/业务类型 Badge | ✅ | 5 种颜色区分 |
| API 返回 DTO（非 Entity） | ✅ | `OperateLogResponse` DTO，通过 `OperateLogConverter` 转换 |
| 日志导出 | ❌ | 未实现 |
| 保留策略 | ❌ | 无自动清理 |
| 敏感字段脱敏 | ❌ | API Key 哈希可能出现在 JSON 快照中 |
| Storage 层抽象 | ❌ | `OperateLogService` 直接继承 `ServiceImpl`，未走 Storage 接口 |

### 5.5 管理员账号管理

| 功能 | 实现状态 | 说明 |
|------|---------|------|
| 登录（JWT 8h 过期） | ✅ | `POST /api/auth/login` |
| 滑动续期（剩余 < 30min） | ✅ | 拦截器自动签发新 Token，`X-New-Token` 响应头 |
| 改密码后旧 Token 失效（方案 C） | ✅ | JWT iat vs `passwordChangedAt` |
| 管理员列表（仅 SUPER_ADMIN） | ✅ | Service 层 `requireSuperAdmin()` 校验 |
| 创建/编辑/删除管理员 | ✅ | BCrypt 密码哈希 + 安全保护规则 |
| 修改自己密码/昵称 | ✅ | 任意角色可用 |
| 安全保护（默认 admin / 不可删自己） | ✅ | 后端校验返回 403 |
| 登录失败锁定 | ❌ | 未实现 |
| 密码复杂度策略 | ❌ | 未实现 |
| 登录日志 | ❌ | 仅记录业务操作，不记录登录行为 |

### 5.6 客户端 Open API

| 功能 | 实现状态 | 说明 |
|------|---------|------|
| 单条/批量配置上报 | ✅ | 已存在跳过，不覆盖；三态返回 (added/exist/retired)，tombstone 不复活 |
| 短轮询拉取（304 优化） | ✅ | 版本号对比 |
| 长轮询订阅（DeferredResult） | ✅ | 超时 1-60s 可配，补偿轮询兜底 |
| 统计上报 | ✅ | 17+ 指标异步写入 |
| API Key 认证 | ✅ | `X-App-Id` + `X-API-Key` |
| 限流（100 次/60s per appId） | ✅ | Redisson RRateLimiter（仅 db profile） |
| **删除配置→客户端退管（SDK 侧）** | ✅ | `ConfigPollingService.updatePools` 已实现 diff + `revertToLocalConfig()`；`RemoteConfigSourcePoolManager.handleAddResult` 已处理 retired 三态 |
| **删除配置→客户端退管（Server 侧）** | ⚠️ | `deleteConfig` 单个删除已触发 `triggerListeners`；`deleteConfigs` 批量删除仅 `unregister` 未触发通知 |
| 健康检查端点 | ❌ | `ApiKeyAuthInterceptor` 排除了 `/health` 路径但无实际实现 |
| mock 接口暴露生产 | ✅ 已修复 | `POST /api/stats/mock` 已添加 `@Profile("local")` 限制 |
| 多实例长轮询 | ❌ | 订阅者仅注册在单个实例内存 |

### 5.7 认证与安全

| 功能 | 实现状态 | 说明 |
|------|---------|------|
| 密码 BCrypt 哈希 | ✅ | `spring-security-crypto` |
| API Key BCrypt 哈希 | ✅ | 32 位随机字符串 |
| JWT 签名密钥可配置 | ✅ | 默认值需生产环境更换 |
| 认证拦截器分离 | ✅ | `/api/**` JWT, `/open/api/**` API Key |
| ThreadLocal 上下文清理 | ✅ | `afterCompletion` 清除 |
| 全局异常处理 | ✅ | 语义化 HTTP 状态码 + `ApiResponse` |
| Admin API 限流 | ❌ | 仅 Open API 有限流 |
| CORS 配置 | ❌ | 未配置 |
| CSRF 保护 | ❌ | JWT + localStorage 模式下影响有限 |

### 5.8 系统架构

| 功能 | 实现状态 | 说明 |
|------|---------|------|
| 双 Profile（local: H2+Caffeine, db: MySQL+Redisson） | ✅ | Profile 切换透明 |
| 优雅停机 | ✅ | 3 个隔离线程池 + `ExecutorUtils.shutdown()` |
| 缓存层抽象（CacheService + CachedStorageSupport） | ✅ | local: CaffeineCache / remote: RedissonCache，`getOrLoad` 双重检查 + SyncLock |
| 配置变更监听器 | ✅ | `ConfigChangeListenerManager` + `CopyOnWriteArrayList` |
| 异步审计日志 | ✅ | `@Async` + `AdminUserContextTaskDecorator` 上下文传递 |
| 游标分页 | ✅ | `cursorQueryByAppIds` 解决大数据量 |
| 异常类命名规范 | ✅ | `ResourceAlreadyExistsException` 拼写正确 |
| Spring Boot Actuator | ❌ | 未集成 |
| 统计数据清理 | ❌ | `thread_pool_stats` 表无限增长 |
| 统计预聚合表 | ❌ | 无 `thread_pool_stats_daily` 表 |

---

## 6. 功能缺口分析

> 基于 2026-07-15 代码全量验证，按严重度排序。已完成项标注 ✅。

### 6.1 P0 级缺口（影响核心价值）

| # | 缺口 | 模块 | 影响 | 状态 |
|---|------|------|------|------|
| ~~GAP-01~~ | ~~删除配置不通知客户端~~ | 配置管理 | ~~管理员删除线程池配置后，客户端无感知~~ | ⚠️ **部分修复**：单个删除 `deleteConfig` 已触发 `triggerListeners`；批量删除 `deleteConfigs(appId)` 仅 `unregister` 未触发通知，需改为先 `triggerListeners` 再 `unregister` |
| ~~GAP-02~~ | ~~仪表盘无运行时指标~~ | 仪表盘 | 统计数据已入库但仪表盘未展示运行时指标 | ❌ **待实现**：`DashboardController.summary` 未查询 `thread_pool_stats` 表 |
| ~~GAP-03~~ | ~~mock 接口暴露生产环境~~ | Open API | ~~db profile 下可调用 mock 接口~~ | ✅ **已修复**：`@Profile("local")` 限制 |

### 6.2 P1 级缺口（影响安全/可用性）

| # | 缺口 | 模块 | 影响 | 根因分析 |
|---|------|------|------|---------|
| GAP-04 | **Admin API 无限流** | 认证安全 | `/api/auth/login` 可被暴力破解，其他管理 API 也无频率限制 | `RateLimitInterceptor` 仅注册到 `/open/api/**` 路径 |
| GAP-05 | **登录失败无锁定** | 认证安全 | 暴力破解无防护 | `AdminAuthService.login` 无失败计数 + 锁定逻辑 |
| GAP-06 | **统计数据无清理机制** | 系统架构 | `thread_pool_stats` 表无限增长，长期运行后查询变慢 | 无定时清理任务，无 TTL 配置 |
| GAP-07 | **长轮询多实例失效** | 系统架构 | 多实例部署时，配置变更仅通知同实例的订阅者 | `ConfigChangeListenerManager` 使用进程内 `ConcurrentMap`，未引入 Redis Pub/Sub |
| GAP-08 | **无 Actuator 集成** | 系统架构 | 无健康检查、无运行时指标暴露 | pom.xml 未引入 `spring-boot-starter-actuator` |
| GAP-09 | **无健康检查端点** | Open API | 客户端无法探测 admin-server 连通性 | `ApiKeyAuthInterceptor` 排除了 `/health` 但无 Controller 实现 |
| GAP-10 | **API Key 无过期预警** | API Key 管理 | Key 过期后客户端静默断连，无提前通知 | 列表接口返回 `expired` 布尔值但无即将过期（7 天内）标识 |

### 6.3 P2 级缺口（影响体验/效率）

| # | 缺口 | 模块 | 影响 |
|---|------|------|------|
| GAP-11 | 仪表盘仅 5 条日志无分页 | 仪表盘 | 无法查看更多历史操作 |
| GAP-12 | API Key 列表无搜索框 | API Key 管理 | 大量 Key 时查找困难 |
| GAP-13 | 操作日志无导出 | 审计日志 | 合规审计场景需手动截图 |
| GAP-14 | 配置列表无搜索/过滤 | 配置管理 | 大量线程池时定位困难 |
| GAP-15 | 无批量编辑/模板/导入导出 | 配置管理 | 批量调优效率低 |
| GAP-16 | 前端无 loading 状态 | 全局 | AJAX 请求期间无视觉反馈 |
| GAP-17 | 前端无错误 toast 通知 | 全局 | 错误仅 inline 展示，操作失败可能被忽略 |
| GAP-18 | 无 CORS 配置 | 认证安全 | 跨域访问被浏览器拦截 |

---

## 7. 架构问题诊断

> 基于 2026-07-15 代码全量验证。已完成项标注 ✅。

### 7.1 P0 级架构问题（必须修复）

| # | 问题 | 位置 | 影响 | 状态 |
|---|------|------|------|------|
| ARCH-01 | **ConfigAdminService 多步操作无事务保护** | `ConfigAdminService.createApp` / `deleteApp` / `saveConfig` / `rollback` | `createApp` 先写 API Key 再建 app entry，部分失败导致数据不一致；`saveConfig` 先写配置再记快照，快照可能丢失 | ❌ **待修复**：所有写操作方法均无 `@Transactional` |
| ~~ARCH-02~~ | ~~DAO Entity 泄露到 API 层~~ | ~~ConfigAdminService / OperateLogController~~ | ~~内部数据库结构暴露给前端~~ | ✅ **已修复**：Controller/Service 层均返回 DTO（`OperateLogResponse`、`ThreadPoolConfigItemResponse`），通过 Converter 转换 |
| ARCH-03 | **OperateLogService 绕过 Storage 抽象** | `OperateLogService` 直接继承 `ServiceImpl<OperateLogMapper, OperateLogEntity>` | 违反分层架构，操作日志无法替换存储实现 | ❌ **待修复**：`OperateLogStorage` 接口不存在 |

### 7.2 P1 级架构问题（应该修复）

| # | 问题 | 位置 | 影响 | 状态 |
|---|------|------|------|------|
| ARCH-04 | **事务注解不一致** | `ThreadPoolConfigPersistenceService` 中 3 个方法用裸 `@Transactional`（`createAppEntry`、`restoreConfigByAppIdAndPoolName`、`restoreConfigsByAppId`），4 个用 `@Transactional(rollbackFor=Exception.class)` | checked exception 时回滚行为不可预测 | ⚠️ **部分修复**：4/7 方法已正确，3/7 需补充 `rollbackFor` |
| ~~ARCH-05~~ | ~~AdminAuthService 不是 Spring Bean~~ | ~~`AdminServerAutoConfiguration` 第 131 行通过 `@Bean` 注册~~ | ✅ **非问题**：`@Bean` 方法返回的对象同样由 Spring 容器管理，完全支持 AOP 代理（与 `@Service` 扫描等效） |
| ARCH-06 | **角色鉴权未在 Controller 层** | Controller Javadoc 说"仅 SUPER_ADMIN"但无 `@PreAuthorize` | 权限校验散落在 Service 层，容易遗漏 | ❌ **待修复** |
| ARCH-07 | **API 路径不一致** | `GET /open/api/thread-pool/config/{appId}/pull` 使用单数 | `add` 路径已统一为复数 `configs`，但 `pull` 仍为单数 `config` | ⚠️ **部分修复**：`add` 已统一，`pull` 待修复 |
| ~~ARCH-08~~ | ~~异常类拼写错误~~ | ~~`ResoureAlreadyExistsException`~~ | ~~代码质量问题~~ | ✅ **已修复**：已重命名为 `ResourceAlreadyExistsException` |
| ~~ARCH-09~~ | ~~本地缓存可能失效~~ | ~~`LocalCacheService.getMap()`~~ | ~~缓存实例不复用~~ | ✅ **已修复**：`LocalCacheService` 已移除，缓存系统重构为 `Cache` 接口 + `CachedStorageSupport` 基类（Caffeine/Redisson 实现），不再有此问题 |

### 7.3 P2 级架构问题（建议优化）

| # | 问题 | 位置 | 影响 | 修复建议 |
|---|------|------|------|---------|
| ARCH-10 | **application.yml 注释与值矛盾** | 注释说"默认 local 模式"但 `active: db` | 开发者困惑 | 修正注释或改为 `active: local`（开发友好） |
| ARCH-11 | **MyBatis SQL 日志在所有环境开启** | `log-impl: StdOutImpl` | 生产环境 SQL 日志影响性能 | 改为 `log-impl: org.apache.ibatis.logging.slf4j.Slf4jImpl`，通过 logging.level 控制开关 |
| ARCH-12 | **前端 edit-modal HTML 结构可能缺失** | `thread-pools.html` 第 89-99 行 | `openModal('edit-modal')` 可能找不到元素 | 检查并修复 HTML 结构 |

---

## 8. 依赖与前置条件

> v2.0 采用**联合发布**策略（admin-server + client-sdk + core 一起发版），以下标注各需求的模块归属。
> 符号说明：🅰 admin-server 侧改动 | 🅲 client-sdk 侧改动 | 🅺 core 侧改动 | 🔗 跨模块联动

### 8.0 跨模块依赖矩阵

| 需求编号 | 依赖的模块 | 被依赖的模块 | 类型 |
|---------|-----------|------------|------|
| F2-C-01（删除通知） | 🅰 `triggerListeners` 修复 `deleteConfigs` | 🅲 diff + revert + localDeclaredConfig | ✅ 🅲 侧已完成，🅰 侧仅剩 `deleteConfigs` 修复 |
| F2-D-01（运行指标） | 🅰 DashboardController 查询 stats | 🅲 `getStats()` 已实现（无改动） | ✅ 无阻塞 |
| F2-D-04/F2-D-05（趋势图） | 🅰 查 `thread_pool_stats_daily` | 🅰 预聚合定时任务 | 🅰 自闭环 |
| F2-M-01（告警 MVP） | 🅰 仪表盘 Badge + operate_log | 🅲 `getStats()` 已上报 rejected | ✅ 无阻塞 |
| F2-O-03（多实例） | 🅰 Redis Pub/Sub | 🅲 无需改动 | v3.1 交付 |
| F2-T-04（统计聚合+清理） | 🅰 预聚合表 + 定时任务 | 🅲 `getStats()` 已上报 | 🅰 自闭环 |

### 8.0 客户端 SDK 前置改造清单（v3.0 联合发布）

| # | 改造项 | 文件 | 状态 |
|---|--------|------|------|
| ~~SDK-01~~ | ~~`DynamicThreadPoolWrapper` 新增 `localDeclaredConfig` 字段~~ | ~~`core/DynamicThreadPoolWrapper.java`~~ | ✅ **已完成**：字段 + `revertToLocalConfig()` 方法已实现 |
| ~~SDK-02~~ | ~~`ConfigPollingService.applyConfigs` 增加 diff + revert 逻辑~~ | ~~`client/ConfigPollingService.java`~~ | ✅ **已完成**：`updatePools` 已实现 diff（本地有但服务端无的池→`revertToLocalConfig()`） |
| ~~SDK-03~~ | ~~启动时 `registerConfigs` 处理 tombstone 409 响应~~ | ~~`manager/RemoteConfigSourcePoolManager.java`~~ | ✅ **已完成**：`handleAddResult` 已处理三态（exist→对齐，retired→revertToLocalConfig） |

> **v3.0 状态**：SDK 侧配置退管已全链路落地。仅剩 🅰 侧 `deleteConfigs(appId)` 批量删除未触发 `triggerListeners` 的修复。

---

## 9. v2 建设规格

> 本章节定义 v2 迭代需要建设的功能，按优先级分级。每个需求标注模块归属：🅰 / 🅲 / 🅺 / 🔗。

### 9.1 模块一：仪表盘增强

#### P0 - v2.0 必须实现

| 需求编号 | 需求描述 | 验收条件 | 对应缺口 |
|---------|---------|---------|---------|
| F2-D-01 | 增加"运行指标摘要"卡片：展示当前有异常的线程池数量（拒绝率 > 5% 或队列使用率 > 80%） | 从 `thread_pool_stats` 表查询最近 5 分钟数据计算 | GAP-02 |
| F2-D-02 | 仪表盘操作记录改为 20 条 + 分页加载更多 | 点击"加载更多"追加 20 条 | GAP-11 |
| ~~F2-D-03~~ | ~~移除 `POST /api/stats/mock` 在 db profile 下的可用性~~ | ✅ **已完成**：已添加 `@Profile("local")` | ~~GAP-03~~ |

#### P1 - v2.1 实现

| 需求编号 | 需求描述 | 验收条件 |
|---------|---------|---------|
| F2-D-04 | 增加"近 7 天配置变更趋势"折线图 | 使用 Chart.js 渲染，数据从 `operate_log` 按天聚合 |
| F2-D-05 | 增加"近 7 天线程池异常趋势"折线图（拒绝任务数/错误任务数） | 数据从 `thread_pool_stats` 按天聚合 |
| F2-D-06 | 统计卡片支持点击跳转到对应管理页 | 应用数 → 配置管理页，Key 数 → Key 管理页 |

### 8.2 模块二：线程池配置管理增强

#### P0 - v2.0 必须实现

| 需求编号 | 需求描述 | 验收条件 | 对应缺口/问题 |
|---------|---------|---------|-------------|
| F2-C-01 | 🔗 **删除配置触发客户端通知与退管** | ⚠️ **部分完成**：🅲 侧 SDK 全链路已实现（diff + revert + tombstone 三态处理）；🅰 侧 `deleteConfig` 单个删除已触发 `triggerListeners`，但 `deleteConfigs(appId)` 批量删除仅调用 `unregister` 未触发通知。**待修复**：`deleteConfigs` 改为先 `triggerListeners(appId)` 再 `unregister(appId)`。📄 详见 [ADR-0004](../docs/adr/0004-server-unmanage-not-pool-destroy.md) | GAP-01 (部分) |
| ~~F2-C-02~~ | ~~修复 DAO Entity 泄露~~ | ✅ **已完成**：Controller/Service 层均返回 DTO | ~~ARCH-02~~ |
| F2-C-03 | ConfigAdminService 多步操作添加事务保护 | `createApp` / `deleteApp` / `saveConfig` / `rollback` 方法添加 `@Transactional(rollbackFor = Exception.class)` | ARCH-01 |

#### P1 - v2.1 实现

| 需求编号 | 需求描述 | 验收条件 |
|---------|---------|---------|
| F2-C-04 | 配置列表增加搜索框（按池名称模糊搜索） | 前端实时过滤 |
| F2-C-05 | 批量编辑：选中多个线程池，批量修改同一参数 | Checkbox 多选 + 批量编辑弹窗 |
| F2-C-06 | 配置模板：预设"高吞吐"/"低延迟"/"IO 密集型"模板 | 模板选择后预填表单 |

#### P2 - v2.2 考虑

| 需求编号 | 需求描述 |
|---------|---------|
| F2-C-07 | 配置导入/导出（JSON/YAML 格式） |
| F2-C-08 | 配置灰度发布（按 appId 百分比逐步推送） |

### 8.3 模块三：API Key 管理增强

#### P1 - v2.1 实现

| 需求编号 | 需求描述 | 验收条件 | 对应缺口 |
|---------|---------|---------|---------|
| F2-A-01 | API Key 列表增加搜索框（按 appId/appName 搜索） | 前端实时过滤 | GAP-12 |
| F2-A-02 | API Key 过期前 7 天展示黄色警告标识 | 后端返回 `expiringSoon` 字段，前端渲染警告 Badge | GAP-10 |

#### P2 - v2.2 考虑

| 需求编号 | 需求描述 |
|---------|---------|
| F2-A-03 | API Key 绑定 IP 白名单 |
| F2-A-04 | API Key 读写权限分离 |

### 8.4 模块四：操作审计日志增强

#### P1 - v2.1 实现

| 需求编号 | 需求描述 | 验收条件 | 对应缺口/问题 |
|---------|---------|---------|-------------|
| F2-L-01 | 修复 OperateLogService 绕过 Storage 层 | 创建 `OperateLogStorage` 接口 + 实现，Controller 通过 Service 调用 | ARCH-03 |
| F2-L-02 | 修复 OperateLogEntity 泄露到 API 层 | 增加 `OperateLogResponse` DTO | ARCH-02 |
| F2-L-03 | 日志导出 CSV | `GET /api/operate-logs/export` 返回 CSV 文件流 | GAP-13 |

#### P2 - v2.2 考虑

| 需求编号 | 需求描述 |
|---------|---------|
| F2-L-04 | 日志保留策略（超过 N 天自动归档/清理） |
| F2-L-05 | 敏感字段脱敏（JSON 快照中 API Key 哈希脱敏） |

### 8.5 模块五：管理员账号安全增强

#### P0 - v2.0 必须实现

| 需求编号 | 需求描述 | 验收条件 | 对应缺口/问题 |
|---------|---------|---------|-------------|
| ~~F2-U-01~~ | ~~AdminAuthService 添加 `@Service` 注解~~ | ✅ **非问题**：`AdminServerAutoConfiguration` 中通过 `@Bean` 注册，已由 Spring 容器管理，AOP 代理正常工作 | ~~ARCH-05~~ |
| F2-U-02 | 角色鉴权改为 Controller 层注解 | 自定义 `@RequireSuperAdmin` AOP 注解或 Spring Security `@PreAuthorize` | ARCH-06 |

#### P1 - v2.1 实现

| 需求编号 | 需求描述 | 验收条件 | 对应缺口 |
|---------|---------|---------|---------|
| F2-U-03 | 登录失败锁定：连续 5 次失败锁定 15 分钟 | 基于 Redis（db profile）或内存（local profile）计数 | GAP-05 |
| F2-U-04 | Admin API 限流：登录接口 10 次/分钟，其他管理接口 60 次/分钟 | `RateLimitInterceptor` 扩展到 `/api/**` 路径 | GAP-04 |
| F2-U-05 | 密码复杂度策略：最小 8 位，必须含大小写字母 + 数字 | 前端即时校验 + 后端 `@Pattern` 校验 | — |

### 8.6 模块六：Open API 增强

#### P0 - v2.0 必须实现

| 需求编号 | 需求描述 | 验收条件 | 对应缺口/问题 |
|---------|---------|---------|-------------|
| F2-O-01a | 🅰 健康检查端点 `GET /open/api/thread-pool/health` | **目标**：为 client-side HA Router 提供节点状态输入。<br>**三态响应**：`status=UP|DEGRADED|DOWN`，字段包含 `db=UP|DOWN`、`redis=UP|DOWN|N/A`、`timestamp`。<br>**判定规则**：DB DOWN → `DOWN` + HTTP 503；DB UP + Redis UP → `UP` + HTTP 200；DB UP + Redis DOWN → `DEGRADED` + HTTP 200；local profile 下 Redis 为 `N/A`，不参与整体健康判定。Redis DOWN 不视为节点不可用，因为 admin-server 使用 cache-aside，可回源 DB。 | GAP-09 / ADR-0005 |
| F2-O-01b | 🅲 client-sdk HA Router + 客户端故障切换 | **配置模型**：显式 `thread.pool.remote.mode=single|cluster`；`single` 只允许一个地址，`cluster` 必须两个及以上地址，配置不一致 fail-fast；多地址复用 `server-url` 逗号分隔，权重跟随地址（如 `http://host1:8080,http://host2:8080:3`）。<br>**架构**：新增 `FailoverRouter` 独立层，`ConfigServerClient` 保持单节点 HTTP 门面；`ThreadPoolStatsReporter` 的 HTTP 上报合并进 `ConfigServerClient`，统一使用同一套 HTTP connection 与 failover。<br>**路由算法**：支持 `round-robin` / `weighted-round-robin` / `random` / `failover`，默认 `round-robin`；长轮询每次重连重新路由，不做粘性。<br>**failover 触发**：IOException 和 HTTP 5xx 触发；HTTP 4xx、业务码 `code != 0`、长轮询 30s 自然超时不触发。当前节点失败后立即尝试下一节点；所有节点重试一遍仍失败则上抛，由 `ConfigPollingService` 现有指数退避接管。<br>**健康刷新**：后台线程探测全部节点，默认 30s；全部节点不可用时加速到 5s；一次成功即恢复。<br>**节点级熔断**：默认连续失败阈值 3、冷却 30s，HALF_OPEN 只放行 1 个试探请求；熔断与健康检查独立，不按接口维度隔离。<br>**DEGRADED 降级**：`DEGRADED` 与 `UP` 同等参与路由；任一可用节点 DEGRADED 时客户端全局进入降级，pull/report 使用可配置降级间隔，长轮询不降级；恢复后自动恢复正常间隔。动态调度按最近一次完成时间计算剩余延迟，避免简单 `schedule(currentInterval)` 导致额外延迟。 | GAP-09 / ADR-0005 |
| F2-O-01c | 🔗 HA 联调测试 | 覆盖 3 节点 cluster 模式：节点宕机自动切换、所有节点失败后上抛并由 PollingService 退避、节点恢复重新加入、健康三态 UP/DEGRADED/DOWN、Redis DOWN 触发 pull/report 全局降级、节点级熔断 CLOSED→OPEN→HALF_OPEN→CLOSED、`failover` 算法不主动回切主节点、StatsReporter 通过 ConfigServerClient 统一上报。 | GAP-09 / ADR-0005 |
| F2-O-02 | 统一 API 路径：废弃 `/config/{appId}/add` 单数路径，统一为 `/configs/{appId}/add` | 旧路径返回 308 重定向或 410 Gone | ARCH-07 |

#### P1 - v2.1 实现

| 需求编号 | 需求描述 | 验收条件 | 对应缺口 |
|---------|---------|---------|---------|
| F2-O-03 | 🅰 长轮询多实例支持：引入 Redis Pub/Sub 广播配置变更。采用方案 A：广播 `{appId, version}` 消息，各实例自主完成本地 DeferredResult | `ConfigChangeListenerManager.triggerListeners` 发布 Redis 消息，各实例订阅后在本地 `ListenerManager` 中查找 DeferredResult 并完成。**v2.0 部署文档标注：当前长轮询仅在同实例内生效，跨实例变更通过客户端短轮询补偿感知。** | GAP-07 |

### 8.7 模块七：系统架构增强

#### P0 - v2.0 必须实现

| 需求编号 | 需求描述 | 验收条件 | 对应缺口/问题 |
|---------|---------|---------|-------------|
| ~~F2-T-01~~ | ~~修复 `ResoureAlreadyExistsException` 拼写错误~~ | ✅ **已完成**：已重命名为 `ResourceAlreadyExistsException` | ~~ARCH-08~~ |
| F2-T-02 | 统一事务注解为 `@Transactional(rollbackFor = Exception.class)` | `ThreadPoolConfigPersistenceService` 中 `createAppEntry`、`restoreConfigByAppIdAndPoolName`、`restoreConfigsByAppId` 3 个方法需补充 `rollbackFor` | ARCH-04 |
| ~~F2-T-03~~ | ~~修复 `LocalCacheService.getMap()` 缓存实例不复用问题~~ | ✅ **已完成**：`LocalCacheService` 已移除，缓存系统重构为 `Cache` 接口 + `CachedStorageSupport`（Caffeine/Redisson 实现） | ~~ARCH-09~~ |

#### P1 - v2.1 实现

| 需求编号 | 需求描述 | 验收条件 | 对应缺口 |
|---------|---------|---------|---------|
| F2-T-04 | 🅰 统计数据预聚合（支撑 F2-D-04/F2-D-05 趋势图） | 新增 `thread_pool_stats_daily` 预聚合表（按 `(app_id, pool_name, date)` 聚合），`@Scheduled` 每日凌晨执行聚合任务（先聚合后清理）。⚡ **分布式锁**：在 `RedissonSyncLock` 中新增 `tryLock(String lockName, String key, long waitTime, long leaseTime, TimeUnit)` 方法；`StatsAggregationService` 注入 `SyncLock`，方法入口调用 `syncLock.tryLock("stats", "aggregation", 0, 120, SECONDS)`，未获取到锁的节点跳过执行。日聚合表保留 365 天 | — |
| F2-T-05 | 🅰 原始统计表定时清理 | `thread_pool_stats`（原始 10s 粒度）保留 7 天，`@Scheduled` 每日凌晨执行 `DELETE WHERE collect_time < NOW() - 7d`。⚡ **分布式锁**：与 F2-T-04 共用同一把锁 `stats:aggregation`，聚合完成后再执行清理，避免聚合和清理并发读写 | GAP-06 |
| F2-T-06 | CORS 配置支持 | 通过配置项 `threadpool.admin.cors.allowed-origins` 控制 | GAP-18 |
| F2-T-07 | 集成 Spring Boot Actuator | 暴露 `/actuator/health` 和 `/actuator/metrics`，health 端点无需认证 | GAP-08 |

#### P2 - v2.2 考虑

| 需求编号 | 需求描述 |
|---------|---------|
| F2-T-08 | 修正 application.yml 注释与值矛盾 |
| F2-T-09 | MyBatis SQL 日志改为 SLF4J，生产环境通过 logging.level 控制 |
| F2-T-10 | 前端增加 loading 状态和 toast 通知 |

### 8.8 模块八：监控与告警（v2.0 MVP）

> 完整告警系统（Webhook、多通道、分级升级、自定义阈值）推迟至 v2.2。v2.0 聚焦仪表盘内轻量告警。

#### P0 - v2.0 必须实现

| 需求编号 | 需求描述 | 验收条件 | 模块 |
|---------|---------|---------|------|
| F2-M-01 | 🅰 仪表盘异常线程池红色 Badge 标记 | 从 `thread_pool_stats` 查询最近 5 分钟数据，计算每个池的拒绝率（rejected/submitted）和队列使用率（queueSize/queueCapacity）。拒绝率 > 5% 或队列使用率 > 80% 的池在仪表盘显示红色 Badge 和异常计数 | 🅰 |
| F2-M-02 | 🅰 告警事件记录到 operate_log | 异常池首次触发时写一条 operate_log（bizType=THREAD_POOL_STATS, operateType=ALERT），30 分钟内同池同指标不重复记录 | 🅰 |
| F2-M-03 | 🅰 告警恢复记录 | 指标恢复正常（拒绝率 < 1% 且队列使用率 < 50%）时写一条 operate_log（operateType=ALERT_RECOVERED） | 🅰 |

**告警判断逻辑**（P0 仅在后端计算，前端展示）：

```
滑动窗口 = 最近 5 分钟（collect_time > NOW() - 5min）
拒绝率 = SUM(rejectedTaskCount) / SUM(submittedTaskCount)  -- 聚合窗口中所有上报记录
队列使用率 = AVG(queueSize / queueCapacity)
异常判定 = 拒绝率 > 5% OR 队列使用率 > 80%
去重规则 = (appId, poolName, 指标类型) 触发后 30 分钟内不重复
```

#### P2 - v2.2 考虑

| 需求编号 | 需求描述 |
|---------|---------|
| F2-M-04 | Webhook 告警通知（JSON payload 推送到可配置 URL） |
| F2-M-05 | 按池自定义告警阈值（覆盖全局默认值） |
| F2-M-06 | 告警分级（WARNING/CRITICAL）与升级策略 |
| F2-M-07 | 告警静默窗口（按时间段/按应用配置） |

---

## 9. 非功能需求

### 9.1 性能

| 指标 | v1 目标 | v2 目标 | 备注 |
|------|---------|---------|------|
| 管理后台页面首屏加载 | < 2s | < 1.5s | v2 引入前端 loading 状态优化体验 |
| API 响应时间（P95） | < 500ms | < 300ms | v2 优化缓存命中率 |
| 配置查询（单应用 50 线程池） | < 200ms | < 100ms | 缓存命中时 |
| 长轮询并发连接数 | >= 500 | >= 1000 | v2 引入 Redis Pub/Sub 后多实例分担 |
| 统计上报吞吐量 | >= 1000 TPS | >= 2000 TPS | v2 优化批量写入 |

### 9.2 可用性

| 指标 | v1 目标 | v2 目标 |
|------|---------|---------|
| 管理后台可用性 | 99.5%（单实例） | 99.9%（多实例 + 负载均衡） |
| 配置数据持久性 | 数据库保证 | 数据库保证 + 事务保护 |
| 客户端配置拉取 | Open API 独立于管理后台 | 同 v1 |

### 9.3 安全性

| 要求 | v1 实现 | v2 增强 |
|------|---------|---------|
| 密码存储 | BCrypt 哈希 | + 密码复杂度策略 |
| API Key 存储 | BCrypt 哈希 | 同 v1 |
| 传输安全 | HTTPS（外部代理） | 同 v1 |
| JWT 签名密钥 | 外部化配置 | + 启动时校验非默认值 |
| 认证失败信息 | 统一模糊错误 | 同 v1 |
| Admin API 限流 | 无 | + 登录接口 10 次/分钟 |
| 登录失败锁定 | 无 | + 5 次失败锁定 15 分钟 |

### 9.4 可扩展性

| 要求 | v1 状态 | v2 目标 |
|------|---------|---------|
| 水平扩展 | 长轮询单实例内存 | + Redis Pub/Sub 多实例广播 |
| 存储实现可替换 | Storage 接口可替换 | + OperateLogStorage 接口补全 |

### 9.5 可观测性

| 要求 | v1 状态 | v2 目标 |
|------|---------|---------|
| 操作审计 | 全量审计日志 | 同 v1 |
| 数据库慢查询 | StdOutImpl（开发用） | SLF4J + logging.level 控制 |
| 健康检查 | 无 | Actuator `/actuator/health` |
| 运行时指标 | 无 | Actuator `/actuator/metrics` |

---

## 10. 成功指标

### 10.1 北极星指标

**配置变更从"修改完成"到"客户端生效"的端到端延迟（P95）< 35s**

这是衡量系统核心价值——"动态调参实时性"的关键指标。

### 10.2 驱动指标

| 指标 | 基线 | 目标（3 个月） | 测量方式 |
|------|------|---------------|---------|
| 管理后台月活管理员数 | N/A | >= 5 人 | 登录日志统计 |
| 托管应用数 | 当前代码中 samples 应用数 | >= 20 | `thread_pool_config_app` 表 `COUNT(DISTINCT app_id)` |
| 托管线程池总数 | 当前代码中配置数 | >= 100 | `thread_pool_config` 表 `COUNT(*)` |
| 月均配置变更次数 | N/A | >= 50 | `operate_log` 表按月计数 |
| 配置回滚次数占比 | N/A | < 10% | 回滚次数 / 总变更次数 |
| 客户端长轮询订阅成功率 | N/A | > 99% | DeferredResult 超时 vs 正常返回比例 |
| **删除配置客户端同步率** | 0%（v1 不通知） | 100%（v2） | 删除操作触发通知的比例 |

### 10.3 健康指标

| 指标 | 告警阈值 | 说明 |
|------|---------|------|
| API 响应 P99 延迟 | > 2s | 数据库或缓存异常 |
| Open API 认证失败率 | > 10% | 大量无效 Key 或攻击 |
| 长轮询连接数 | > 800（单实例） | 接近线程池上限 |
| 统计上报写入延迟 | > 10s | 数据库写入阻塞 |
| 管理员登录失败率 | > 20% | 暴力破解风险 |
| **线程池拒绝率** | > 5% | 需要扩容或调参 |
| **队列使用率** | > 80% | 队列即将积压 |

---

## 11. 技术架构概要

### 11.1 模块定位

```
                    ┌─────────────────────────┐
                    │   ThreadPool Admin UI    │
                    │   (HTML/CSS/JS SPA)      │
                    └──────────┬──────────────┘
                               │ JWT Bearer Token
                    ┌──────────▼──────────────┐
                    │   Admin API Controllers   │
                    │   Auth / ApiKey / Config  │
                    │   Dashboard / OperateLog  │
                    │   AdminUser / Stats       │
                    └──────────┬──────────────┘
                               │
         ┌─────────────────────┼─────────────────────┐
         │                     │                     │
  ┌──────▼──────┐   ┌─────────▼────────┐   ┌────────▼────────┐
  │  Admin Auth  │   │  Application      │   │  Open API       │
  │  (JWT+BCrypt)│   │  Services         │   │  /open/api/**   │
  │  +Rate Limit │   │  ConfigAdminSvc   │   │  (API Key Auth) │
  │  +Lockout    │   │  ApiKeyAdminSvc   │   │  +Rate Limit    │
  └──────────────┘   │  SubscriptionSvc  │   │  +Health Check  │
                     └────────┬──────────┘   └─────────────────┘
                              │
                    ┌─────────▼────────┐
                    │  Storage Layer    │
                    │  ConfigStorage    │
                    │  ApiKeyStorage    │
                    │  StatsStorage     │
                    │  AdminUserStorage │
                    │  OperateLogStorage│ (v2 新增)
                    │  SnapshotStorage  │
                    └────────┬──────────┘
                             │
              ┌──────────────┼──────────────┐
              │              │              │
      ┌───────▼──────┐ ┌────▼─────┐ ┌──────▼──────┐
      │ CacheService  │ │  MySQL   │ │ Redis Pub/Sub│
      │ Local: CHM    │ │  7 tables│ │ (v2 多实例)  │
      │ Remote:Redis  │ │          │ │              │
      └───────────────┘ └──────────┘ └─────────────┘
```

### 11.2 数据库设计（8 张表）

| 表名 | 核心字段 | 唯一约束 | v2 变更 |
|------|---------|---------|---------|
| `admin_user` | username, password_hash, role, nickname, password_changed_at | `username` UNIQUE | 无变更 |
| `api_key` | app_id, api_key_hash, app_name, enabled, expire_time | `app_id` UNIQUE | 无变更 |
| `thread_pool_config_app` | app_id, version | `app_id` UNIQUE | 无变更 |
| `thread_pool_config` | app_id, pool_name, core/max/queue/reject/... | `(app_id, pool_name)` UNIQUE | 无变更 |
| `thread_pool_stats` | app_id, pool_name, submitted/rejected/error/completed/... , collect_time | 索引 `(app_id, pool_name, collect_time)` | + 原始表保留 7 天定时清理 |
| `thread_pool_stats_daily` | app_id, pool_name, date, avg_submitted, avg_rejected, max_queue_size, ... | `(app_id, pool_name, date)` UNIQUE | **v2.0 新增**：每日凌晨聚合，保留 365 天 |
| `operate_log` | biz_type, operate_type, content(JSON), operator, biz_id | 无 | 无变更 |
| `config_history` | app_id, pool_name, version, config_value(JSON), operator | `(app_id, pool_name, version)` | 无变更 |

所有表使用逻辑删除（`deleted` 字段 0/1）。

### 11.3 分层架构（v2 目标）

```
web 层
  controller/            仅做：参数绑定、调 service、包装 ApiResponse
  controller/dto/
    ├─ request/          所有 @RequestBody（带校验注解）
    └─ response/         所有出参
应用服务层
  service/               业务编排，Controller 唯一依赖
                         ConfigAdminService / ApiKeyAdminService / ...
                         SubscriptionService / StatsAdminService
持久化抽象层
  storage/               Storage 接口（ConfigStorage / ApiKeyStorage / ...）
                         + CachedStorageSupport 缓存基类
                         + ConfigChangeListenerManager
持久化实现层
  dao/entity             数据库实体
  dao/mapper             MyBatis-Plus Mapper
  dao/rep                Repository（extends ServiceImpl）
领域模型
  bean/                  领域模型（ApiKey, ThreadPoolAppConfig, ...）
转换器
  converter/             MapStruct，Entity ↔ Bean ↔ DTO
```

### 11.4 配置项参考（`application.yml`）

```yaml
threadpool:
  admin:
    auth:
      enabled: true
      username: admin
      password: changeme              # 生产必须更换
      secret: <jwt-signing-key>       # 生产必须更换
      token-expire-minutes: 480       # 8 小时
      renew-threshold-minutes: 30     # 滑动续期阈值
    rate-limit:
      enabled: true                   # Open API 限流开关
      admin-enabled: false            # Admin API 限流开关（v2 新增）
    login-lockout:                    # v2 新增
      max-failures: 5
      lock-duration-minutes: 15
    stats:
      retention-days: 30              # 统计数据保留天数（v2 新增）
    cors:                             # v2 新增
      allowed-origins: ""
  app-auth-enabled: true              # Open API 认证开关
```

---

## 12. 风险与缓解

| 风险 | 严重度 | v1 状态 | v2 缓解措施 |
|------|--------|---------|------------|
| **多步操作无事务导致数据不一致** | 🔴 高 | 存在 | v2.0 为 ConfigAdminService 所有多步操作添加 `@Transactional` |
| **删除配置不通知客户端** | 🔴 高 | 存在 | v2.0 修复 `deleteConfig` 触发 `triggerListeners` |
| **长轮询多实例订阅丢失** | 🟠 中 | 存在 | v2.1 引入 Redis Pub/Sub 广播 |
| **Admin API 无限流可被暴力破解** | 🟠 中 | 存在 | v2.1 Admin API 限流 + 登录失败锁定 |
| **mock 接口污染生产数据** | 🟠 中 | 存在 | v2.0 按 Profile 限制 mock 端点 |
| **统计数据无限增长** | 🟡 低 | 存在 | v2.1 定时清理任务（默认保留 30 天） |
| **JWT 默认密钥泄露** | 🟠 中 | 存在 | v2.1 启动时校验非默认值 |
| **DAO Entity 泄露到 API 层** | 🟠 中 | 存在 | v2.0 增加 DTO 转换层 |
| **OperateLogService 绕过分层** | 🟡 低 | 存在 | v2.1 创建 OperateLogStorage 接口 |
| **LocalCacheService 缓存可能失效** | 🟡 低 | 存在 | v2.0 修复 Map 实例复用 |
| **配置回滚安全** | 🟡 低 | 二次确认 | 同 v1 |
| **无 CSRF 保护** | 🟡 低 | JWT+localStorage | 影响有限，暂不处理 |

---

## 13. 演进路线图

> **v3.2 联合发布策略**：admin-server + client-sdk + core 一起发版。
> SDK 侧配置退管已全链路落地，仅剩 🅰 侧修复和增强。
> 以下标注每个工作项的模块归属：🅰 admin-server / 🅲 client-sdk / 🅺 core。

### 发布里程碑

| 里程碑 | 版本号 | 范围 | 出口条件 |
|--------|--------|------|---------|
| **M1: 核心修复** | v3.2.0 | 🅰 架构修复（ARCH-01/03/04/06/07）+ 🅰 仪表盘增强（F2-D-01/02）+ 🅰 告警 MVP（F2-M-01~03）+ 🅰 健康检查 + 🅰 `deleteConfigs` 通知修复 | 🅰 侧全部需求完成，CI 通过 |
| **M2: HA Router 配套** | v3.2.0-rc1 | 🅲 client-sdk HA Router（T-13b）+ 🔗 HA 联调测试（T-13c） | 3 节点集群故障切换、熔断恢复、DEGRADED 降级和统计上报统一链路测试通过 |
| **M3: v3.2 正式发布** | v3.2.0 | M1 + M2 联合发布 | 删除退管全链路 + HA Router 全链路全部通过 |

### v3.2 核心修复与 HA 增强（🅰 + 🅲 + 🔗）

> 目标：修复影响核心价值和数据一致性的 P0 级问题
> SDK 侧配置退管已完成；v3.2 新增 HA Router 能力需要 client-sdk 配套改造

| 工作项 | 类型 | 对应编号 | 模块 | 工作量 | 状态 |
|--------|------|---------|------|--------|------|
| `deleteConfigs` 批量删除触发 `triggerListeners` | 功能修复 | F2-C-01 | 🅰 | 0.5d | ❌ 待实现 |
| ConfigAdminService 事务保护 | 架构修复 | F2-C-03 / ARCH-01 | 🅰 | 1d | ❌ 待实现 |
| 统一事务注解（3 个方法补充 rollbackFor） | 架构修复 | F2-T-02 / ARCH-04 | 🅰 | 0.5d | ❌ 待实现 |
| 角色鉴权 Controller 层注解 | 架构修复 | F2-U-02 / ARCH-06 | 🅰 | 1d | ❌ 待实现 |
| API 路径统一（`pull` 单数→复数） | 架构修复 | F2-O-02 / ARCH-07 | 🅰 | 0.5d | ❌ 待实现 |
| OperateLogStorage 抽象 | 架构修复 | F2-L-01 / ARCH-03 | 🅰 | 1d | ❌ 待实现 |
| 仪表盘运行指标摘要卡片 | 功能新增 | F2-D-01 / GAP-02 | 🅰 | 1d | ❌ 待实现 |
| 仪表盘告警 MVP（Badge + operate_log） | 功能新增 | F2-M-01~03 | 🅰 | 1d | ❌ 待实现 |
| 仪表盘日志分页（20 条 + 加载更多） | UX 优化 | F2-D-02 / GAP-11 | 🅰 | 0.5d | ❌ 待实现 |
| 健康检查端点（三态 UP/DEGRADED/DOWN） | 高可用 | F2-O-01a / GAP-09 | 🅰 | 0.5d | ❌ 待实现 |
| client-sdk HA Router + 客户端故障切换 | 高可用 | F2-O-01b / GAP-09 | 🅲 | 2d | ❌ 待实现 |
| HA Router 联调测试 | 集成测试 | F2-O-01c / GAP-09 | 🔗 | 1d | ❌ 待实现 |
| 统计数据预聚合（日聚合表） | 架构新增 | F2-T-04 | 🅰 | 1d | ❌ 待实现 |
| 统计数据定时清理（原始 7d + 聚合 365d） | 运维增强 | F2-T-05 / GAP-06 | 🅰 | 0.5d | ❌ 待实现 |
| 联调测试 | 集成测试 | — | 🔗 | 1d | ❌ 待实现 |
| ~~异常类重命名~~ | ~~架构修复~~ | ~~F2-T-01~~ | ~~🅰~~ | — | ✅ 已完成 |
| ~~LocalCacheService 修复~~ | ~~架构修复~~ | ~~F2-T-03~~ | ~~🅰~~ | — | ✅ 已完成（缓存系统重构） |
| ~~DAO Entity 泄露修复~~ | ~~架构修复~~ | ~~F2-C-02~~ | ~~🅰~~ | — | ✅ 已完成 |
| ~~mock 端点 Profile 限制~~ | ~~安全修复~~ | ~~F2-D-03~~ | ~~🅰~~ | — | ✅ 已完成 |
| ~~SDK: localDeclaredConfig~~ | ~~SDK 改造~~ | ~~SDK-01~~ | ~~🅲~~ | — | ✅ 已完成 |
| ~~SDK: diff + revert~~ | ~~SDK 改造~~ | ~~SDK-02~~ | ~~🅲~~ | — | ✅ 已完成 |
| ~~SDK: tombstone 三态处理~~ | ~~SDK 改造~~ | ~~SDK-03~~ | ~~🅲~~ | — | ✅ 已完成 |

> 🅰 侧工作量：~9.5d | 🅲 侧工作量：~2d（HA Router）| 联调：~2d（删除退管 + HA）| 总计：~13.5d（任务拆分文档当前执行子集为 ~10.5d）

### Next（v3.1 — 安全与可用性增强）

> 目标：补全安全防护、多实例支持、可观测性

| 工作项 | 类型 | 对应编号 |
|--------|------|---------|
| Admin API 限流 | 安全增强 | F2-U-04 / GAP-04 |
| 登录失败锁定 | 安全增强 | F2-U-03 / GAP-05 |
| 密码复杂度策略 | 安全增强 | F2-U-05 |
| 长轮询多实例（Redis Pub/Sub） | 架构增强 | F2-O-03 / GAP-07 |
| Actuator 集成 | 可观测性 | F2-T-07 / GAP-08 |
| 原始统计表定时清理 | 运维增强 | F2-T-05 / GAP-06 |
| CORS 配置支持 | 安全增强 | F2-T-06 / GAP-18 |
| OperateLogStorage 抽象 | 架构修复 | F2-L-01 / ARCH-03 |
| 日志导出 CSV | 功能新增 | F2-L-03 / GAP-13 |
| API Key 过期预警 | 功能新增 | F2-A-02 / GAP-10 |
| 仪表盘趋势图 | 功能新增 | F2-D-04 / F2-D-05 |
| 配置列表搜索 | UX 优化 | F2-C-04 / GAP-14 |
| 批量编辑 + 配置模板 | 功能新增 | F2-C-05 / F2-C-06 |

### Later（v3.2 — 体验优化与扩展）

> 目标：锦上添花

| 工作项 | 类型 |
|--------|------|
| 配置导入/导出 | 功能新增 |
| API Key IP 白名单 | 安全增强 |
| API Key 读写权限分离 | 安全增强 |
| 日志保留策略 + 敏感字段脱敏 | 合规增强 |
| 前端 loading 状态 + toast 通知 | UX 优化 |
| 配置灰度发布 | 功能新增 |
| 前端框架迁移评估（React/Vue） | 工程优化 |
| 完整告警系统（Webhook/分级/自定义阈值） | 功能新增 |

---

## 14. 开放问题

| # | 问题 | 负责人 | 类型 | 状态 | v2 更新 |
|---|------|--------|------|------|---------|
| Q1 | 多实例部署时，长轮询订阅如何跨实例通知？ | 架构 | 非阻塞 | v3.1 解决 | 确认引入 Redis Pub/Sub |
| Q2 | 统计上报数据保留策略？ | 产品 | ✅ **已解决** | **v3.0 解决** | 原始表 7 天 + 日聚合表 365 天 |
| Q3 | 是否需要集成 Spring Boot Actuator？ | 工程 | 非阻塞 | v3.1 解决 | 确认集成 |
| Q4 | 前端是否需要迁移到现代框架？ | 工程 | 非阻塞 | v3.2 评估 | 当前纯 HTML 可满足 v3 |
| Q5 | 客户端 SDK 重连策略？ | 工程 | ✅ **已解决** | v3.2 解决 | 采用 ADR-0005：FailoverRouter + 健康刷新 + 节点级熔断 + PollingService 退避兜底 |
| Q6 | 是否需要支持配置灰度发布？ | 产品 | 非阻塞 | v3.2 评估 | 列入 Later |
| Q7 | 删除配置后客户端是否应同步移除线程池？ | 产品 | ✅ **已解决** | v3.0 解决 | 否——采用配置退管语义（[ADR-0004](../docs/adr/0004-server-unmanage-not-pool-destroy.md)），客户端回退到本地声明配置，不销毁池。SDK 侧已全链路实现 |
| Q8 | mock 接口是否应在生产环境保留？ | 安全 | ✅ **已解决** | v3.0 解决 | 否，已通过 `@Profile("local")` 限制 |
| Q9 | 操作日志是否需要记录登录/登出行为？ | 产品 | 非阻塞 | 待决策 | v3.2 评估 |
| Q10 | 是否需要 Webhook 告警通知机制？ | 产品 | ✅ **已解决** | v3.0 解决 | v3.0 仅做仪表盘 Badge MVP，Webhook 推迟至 v3.2 |

---

## 15. 附录

### A. 完整 API 端点清单（40 个端点）

#### 管理后台 API（JWT 认证，35 个端点）

| 方法 | 路径 | 说明 | v2 变更 |
|------|------|------|---------|
| `POST` | `/api/auth/login` | 管理员登录 | v2.1 增加失败锁定 |
| `POST` | `/api/auth/logout` | 管理员登出 | — |
| `POST` | `/api/api-keys` | 创建 API Key | — |
| `GET` | `/api/api-keys` | 分页查询 API Key 列表 | v2.1 增加搜索 |
| `GET` | `/api/api-keys/{appId}` | 查询单个 API Key | v2.1 增加 expiringSoon |
| `PUT` | `/api/api-keys/{appId}` | 更新 API Key 元数据 | — |
| `DELETE` | `/api/api-keys/{appId}` | 删除 API Key | — |
| `POST` | `/api/api-keys/{appId}/regenerate` | 重新生成 API Key | — |
| `GET` | `/api/thread-pool/configs` | 列出所有应用摘要 | — |
| `GET` | `/api/thread-pool/configs/{appId}` | 获取应用全量配置 | — |
| `GET` | `/api/thread-pool/configs/{appId}/{poolName}` | 获取单个配置 | — |
| `POST` | `/api/thread-pool/configs/{appId}` | 批量保存配置 | v2.0 增加事务 |
| `POST` | `/api/thread-pool/configs/{appId}/{poolName}` | 保存单个配置 | v2.0 增加事务 |
| `POST` | `/api/thread-pool/config/{appId}/add` | 添加单条（存在不覆盖） | v2.0 废弃，统一为 configs |
| `POST` | `/api/thread-pool/configs/{appId}/add` | 批量添加（已存在跳过） | — |
| `DELETE` | `/api/thread-pool/configs/{appId}` | 清空应用配置 | v3.0 触发客户端通知（待修复） |
| `DELETE` | `/api/thread-pool/configs/{appId}/{poolName}` | 删除单个配置 | ✅ 已触发客户端通知 |
| `GET` | `/api/thread-pool/configs/{appId}/version` | 获取配置版本号 | — |
| `GET` | `/api/thread-pool/configs/{appId}/{poolName}/snapshots` | 获取配置快照历史 | — |
| `POST` | `/api/thread-pool/configs/{appId}/{poolName}/rollback` | 回滚到指定版本 | v2.0 增加事务 |
| `POST` | `/api/thread-pool/apps` | 创建应用 | v2.0 增加事务 |
| `DELETE` | `/api/thread-pool/apps/{appId}` | 删除应用 | v2.0 增加事务 |
| `GET` | `/api/thread-pool/configs/deleted` | 列出已删除配置 | ✅ 返回 DTO |
| `PUT` | `/api/thread-pool/configs/{appId}/{poolName}/restore` | 恢复单个已删除配置 | ✅ 返回 DTO |
| `PUT` | `/api/thread-pool/configs/{appId}/restore` | 批量恢复已删除配置 | — |
| `GET` | `/api/dashboard/summary` | 仪表盘聚合数据 | v2.0 增加运行指标 |
| `GET` | `/api/operate-logs/list` | 分页查询操作日志 | v2.1 通过 Storage 层 |
| `GET` | `/api/operate-logs/export` | 导出日志 CSV | v2.1 新增 |
| `GET` | `/api/stats/{appId}/{poolName}` | 查询统计历史 | — |
| `POST` | `/api/stats/mock` | 生成模拟统计数据 | ✅ 已限制 `@Profile("local")` |
| `GET` | `/api/admin-users` | 管理员列表 | v2.0 注解鉴权 |
| `POST` | `/api/admin-users` | 创建管理员 | v2.0 注解鉴权 |
| `PUT` | `/api/admin-users/{username}` | 更新管理员 | v2.0 注解鉴权 |
| `DELETE` | `/api/admin-users/{username}` | 删除管理员 | v2.0 注解鉴权 |
| `PUT` | `/api/admin-users/me/password` | 修改自己密码 | v2.1 密码复杂度 |
| `PUT` | `/api/admin-users/me/nickname` | 修改自己昵称 | — |

#### 开放 API（API Key 认证，5 个端点）

| 方法 | 路径 | 说明 | v2 变更 |
|------|------|------|---------|
| `POST` | `/open/api/thread-pool/config/{appId}/add` | 上报单条配置 | v2.0 废弃 |
| `POST` | `/open/api/thread-pool/configs/{appId}/add` | 批量上报配置 | — |
| `GET` | `/open/api/thread-pool/config/{appId}/pull` | 短轮询拉取配置 | v3.0 统一为复数 `configs` |
| `GET` | `/open/api/thread-pool/configs/{appId}/subscribe` | 长轮询订阅变更 | v2.1 多实例支持 |
| `POST` | `/open/api/thread-pool/stats/report` | 上报运行时统计 | — |
| `GET` | `/open/api/thread-pool/health` | 健康检查 | v2.0 新增 |

### B. 数据流示意图

```
客户端应用启动
  │
  ├─(1)→ POST /open/api/.../configs/{appId}/add  ──→ 上报线程池配置基线
  │
  ├─(2)→ GET /open/api/.../configs/{appId}/subscribe  ──→ 长轮询订阅变更
  │
  ├─(3)→ POST /open/api/.../stats/report  ──→ 定期上报运行时统计（如每 10s）
  │
  └─(4)← 收到配置变更通知 {appId, version}
        │
        └→ GET /open/api/.../config/{appId}/pull ──→ 拉取最新全量配置
                                                      │
                                                      └→ 热更新线程池参数

管理员操作流程
  │
  ├─(A)→ 登录 ──→ JWT Token（8h，滑动续期）
  │
  ├─(B)→ 修改配置 ──→ 保存到 MySQL + 记录快照 + 触发监听器
  │                    │
  │                    └→ ConfigChangeListenerManager.triggerListeners
  │                          │
  │                          ├→ 通知同实例订阅者（v1）
  │                          └→ Redis Pub/Sub 广播（v2）
  │
  ├─(C)→ 删除配置 ──→ 软删除 + v2: 触发监听器通知客户端
  │
  └─(D)→ 回滚配置 ──→ 取快照 + 写回配置 + 记录新快照 + 触发监听器
```

### C. 架构重构进展

基于 `REFACTORING_PROPOSAL.md`（2026-06-20）和 `REFACTORING_PLAN.md`（2026-06-21）：

| 重构项 | v1 状态 | v3.0 状态 |
|--------|---------|---------|
| 删除死代码（controller/dto/ 5 个类） | ✅ 已清理 | — |
| 修复远程模式启动 Bug | ✅ 已修复（统一 MyBatis Storage） | — |
| 统一 Web DTO 模型 | ✅ 已收敛 | — |
| 重建应用服务层 | ✅ 已完成（ConfigAdminService 等） | — |
| DAO Entity 泄露到 API | ✅ 已修复 | ✅ Controller/Service 层均返回 DTO |
| 异常类命名规范 | ✅ 已修复 | ✅ `ResourceAlreadyExistsException` |
| 缓存系统重构 | ✅ 已完成 | ✅ `Cache` 接口 + `CachedStorageSupport` + Caffeine/Redisson |
| Mock 端点 Profile 限制 | ✅ 已修复 | ✅ `@Profile("local")` |
| SDK 配置退管全链路 | ✅ 已完成 | ✅ `localDeclaredConfig` + diff + revert + tombstone 三态 |
| OperateLogService 绕过 Storage | ❌ 未修复 | ❌ v3.0 修复 |
| 事务保护 | ❌ 未修复 | ❌ v3.0 修复 |
| 事务注解一致性 | ❌ 未修复 | ⚠️ 4/7 方法已正确，3/7 待修复 |
| 模型去重 | ⏳ 渐进 | v3.2 持续 |

### D. 异常体系

```
RuntimeException
  └─ BusinessException (code 字段)
       ├─ AuthenticationException       (code=401, 认证失败)
       ├─ AuthForbiddenException        (code=403, 权限不足)
       ├─ ValidationException            (code=400, 参数校验)
       ├─ ResourceNotFoundException      (code=404, 资源不存在)
       ├─ ResourceAlreadyExistsException  (code=409, 资源已存在)  ← ✅ 拼写已修正
       ├─ ResourceNotModifiedException   (code=304, 未修改)
       └─ StorageException               (code=500, 存储异常)
```

### E. 指标字段映射（CONTEXT.md 规范术语 ↔ stats 上报字段）

`DynamicThreadPoolWrapper.getStats()` 上报 17 个字段。以下映射到 [CONTEXT.md](../CONTEXT.md) 规范术语：

| stats 字段 | 类型 | CONTEXT.md 术语 | 口径说明 | 趋势图/告警可用 |
|-----------|------|----------------|---------|---------------|
| `submittedTaskCount` | 累计值 | submitted（已提交） | `execute()` 入口自增，正确 | ✅ 用于拒绝率分母 |
| `completedTaskCount` | 累计值 | completed（已完成） | `wrap()` finally 自增 | ✅ |
| `errorTaskCount` | 累计值 | error（出错） | CF 异常完成层捕获 | ✅ |
| `rejectedTaskCount` | 累计值 | rejected（被拒绝） | `withRejectedCounting()` 包装 | ✅ 用于拒绝率分子 |
| `activeCount` | 瞬时值 | — | `delegate.getActiveCount()` | ✅ |
| `poolSize` | 瞬时值 | — | `delegate.getPoolSize()` | ✅ |
| `largestPoolSize` | 瞬时值 | — | `delegate.getLargestPoolSize()` | — |
| `queueSize` | 瞬时值 | — | `delegate.getQueue().size()` | ✅ 用于队列使用率 |
| `queueCapacity` | 配置值 | — | 来自 current config | ✅ 用于队列使用率分母 |
| `queueRemainingCapacity` | 瞬时值 | — | `queue.remainingCapacity()` | — |
| `corePoolSize` | 配置值 | — | 来自 current config | — |
| `maximumPoolSize` | 配置值 | — | 来自 current config | — |
| `taskCount` | 瞬时值 | — | `delegate.getTaskCount()` | — |
| `isShutdown` | 状态值 | — | pool lifecycle | — |
| `isTerminated` | 状态值 | — | pool lifecycle | — |
| `collectTime` | 元数据 | — | `LocalDateTime.now()` | — |
| `poolName` | 元数据 | — | key identifier | — |

> **告警计算公式**（基于原始表最近 5 分钟窗口）：
> ```
> 拒绝率 = SUM(rejectedTaskCount) / SUM(submittedTaskCount)
> 队列使用率 = AVG(queueSize / queueCapacity)
> ```

---

> **文档维护**：本文档基于 `admin-server` 模块代码逆向分析 + 产品差距分析撰写。v3.0 为代码全量验证后版本（8 项已完成、2 项部分完成、10 项待实现）。SDK 侧配置退管已全链路落地；v3.2 在 ADR-0005 中补全 admin-server 高可用与 client-side HA Router 设计。
