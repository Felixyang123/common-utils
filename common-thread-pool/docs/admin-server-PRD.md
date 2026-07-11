# ThreadPool Admin Server 产品规格文档（PRD）

> **版本**: v1.0  
> **状态**: 已交付（基于代码实现逆向撰写）  
> **模块**: `common-thread-pool/admin-server`  
> **技术栈**: Spring Boot 3.x / JDK 21 / MyBatis-Plus / JWT / BCrypt  
> **作者**: 产品通  
> **日期**: 2026-07-10

---

## 目录

1. [问题陈述](#1-问题陈述)
2. [目标与非目标](#2-目标与非目标)
3. [用户角色](#3-用户角色)
4. [用户故事](#4-用户故事)
5. [功能需求](#5-功能需求)
6. [非功能需求](#6-非功能需求)
7. [成功指标](#7-成功指标)
8. [技术架构概要](#8-技术架构概要)
9. [风险与缓解](#9-风险与缓解)
10. [开放问题](#10-开放问题)
11. [附录](#11-附录)

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

| # | 目标 | 衡量标准 |
|---|------|---------|
| G1 | **集中配置管理**：管理员可通过 Web UI 对所有接入应用的线程池进行增删改查 | 支持至少 100 个应用、每个应用 50+ 线程池配置 |
| G2 | **运行时状态可见**：客户端 SDK 上报线程池运行时指标，管理后台可查询历史数据 | 统计上报延迟 < 5s，保留至少 7 天历史 |
| G3 | **配置变更追溯**：每次配置修改记录版本快照，支持版本对比和回滚 | 快照保留全量历史，回滚操作 < 1s |
| G4 | **实时配置推送**：管理后台修改配置后，客户端通过长轮询在 30s 内感知变更 | 长轮询超时 30s，补偿轮询兜底 |
| G5 | **安全认证**：管理后台使用 JWT 认证，客户端使用 API Key 认证，操作记录审计日志 | 密码 BCrypt 哈希，API Key 仅创建时返回明文 |
| G6 | **多环境适配**：单机开发环境零依赖（H2 + 内存缓存），生产环境高可用（MySQL + Redis） | 通过 Spring Profile 一键切换 |

### 2.2 非目标（Non-Goals）

| # | 非目标 | 原因 |
|---|--------|------|
| NG1 | **不提供线程池自动调参/自适应策略** | 属于智能运维（AIOps）范畴，当前聚焦人工决策 + 手动调参 |
| NG2 | **不替代 APM 监控系统（如 Prometheus + Grafana）** | 统计上报聚焦线程池核心指标，不做全链路追踪或基础设施监控 |
| NG3 | **不支持多租户隔离** | 当前版本假设同一 admin-server 实例内所有应用属于同一信任域 |
| NG4 | **不提供邮件/短信/钉钉告警** | 告警通道依赖外部系统集成，v1 聚焦配置管理核心链路 |
| NG5 | **不支持 OAuth2 / LDAP / SSO 集成** | v1 使用内置用户名密码认证，企业 SSO 为后续迭代方向 |
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

### 4.2 线程池配置管理

> **US-C1**：作为**管理员**，我希望按应用（appId）查看该应用下所有的线程池配置列表，包括池名称、核心/最大线程数、队列类型和容量、拒绝策略，以便了解每个应用的线程池拓扑。

> **US-C2**：作为**管理员**，我希望创建新的线程池配置，设置池名称、核心/最大线程数、队列类型、队列容量、拒绝策略、存活时间、线程名前缀等参数，以便为新业务配置合理的线程池。

> **US-C3**：作为**管理员**，我希望编辑已有的线程池配置（如调整核心线程数或队列容量），修改后客户端能自动感知变更，以便在流量变化时动态调优。

> **US-C4**：作为**管理员**，我希望删除不再需要的线程池配置，以便清理历史遗留的无用配置。

> **US-C5**：作为**管理员**，我希望查看某个线程池配置的历史版本列表，选择任意两个版本进行字段级对比（差异高亮），以便理解配置的演变过程。

> **US-C6**：作为**管理员**，我希望将某个线程池配置回滚到任意历史版本，以便在错误修改后快速恢复。

### 4.3 API Key 管理

> **US-A1**：作为**管理员**，我希望创建一个新的 API Key（绑定 appId、应用名称、过期时间），创建成功后仅展示一次明文 Key，以便安全地分发给接入方。

> **US-A2**：作为**管理员**，我希望查看所有 API Key 的列表（脱敏显示，不包含哈希值），支持分页，以便管理大量接入应用。

> **US-A3**：作为**管理员**，我希望更新 API Key 的元数据（应用名称、描述、启用/禁用状态、过期时间），以便在应用信息变更或需要临时禁用时快速操作。

> **US-A4**：作为**管理员**，我希望重新生成某个 API Key（返回新明文），旧 Key 立即失效，以便在密钥泄露时快速响应。

> **US-A5**：作为**管理员**，我希望删除不再使用的 API Key，以便清理无效凭证。

### 4.4 操作审计日志

> **US-L1**：作为**管理员**，我希望查看全量操作日志（按业务类型、操作类型、操作人、业务 ID 过滤），支持分页，以便在安全审计或故障排查时追溯操作历史。

> **US-L2**：作为**管理员**，我希望每条操作日志记录操作前后的内容快照（JSON 格式），以便精确了解变更细节。

### 4.5 管理员账号管理

> **US-U1**：作为**超级管理员**，我希望创建新的管理员账号（设置用户名、密码、昵称、角色），以便授权其他运维人员使用管理后台。

> **US-U2**：作为**超级管理员**，我希望查看所有管理员列表、编辑管理员信息、禁用或删除管理员，以便管理团队权限。

> **US-U3**：作为**任意管理员**，我希望修改自己的密码和昵称，以便保障个人账户安全。

> **US-U4**：作为**系统**，默认 `admin` 用户不可删除、不可禁用、角色不可更改，管理员不能删除/禁用自己，以防止误操作导致无法登录。

### 4.6 客户端 SDK 接入（Open API）

> **US-O1**：作为**客户端应用**，我希望在启动时将自己的线程池配置上报到 admin-server（已存在的配置自动跳过），以便服务端有全量的配置基线。

> **US-O2**：作为**客户端应用**，我希望通过短轮询接口拉取服务端的最新配置（支持版本号对比，未变更返回 304），以便在主动检查时获取最新配置。

> **US-O3**：作为**客户端应用**，我希望通过长轮询接口订阅配置变更，当管理员修改配置后在 30s 内收到变更通知（仅含 appId + 版本号），以便实时热更新线程池参数。

> **US-O4**：作为**客户端应用**，我希望定期上报线程池运行时统计（活跃线程数、队列大小、已完成任务数、错误数、拒绝数等），以便管理后台展示运行状态。

### 4.7 认证与安全

> **US-S1**：作为**管理员**，我希望通过用户名密码登录管理后台（JWT 8 小时过期），在过期前 30 分钟内操作自动滑动续期，修改密码后旧 Token 立即失效，以兼顾安全性和使用便利性。

> **US-S2**：作为**客户端应用**，我希望通过 `X-App-Id` + `X-API-Key` Header 认证调用 Open API，密钥哈希存储（BCrypt），支持启用/禁用和过期控制。

---

## 5. 功能需求

### 5.1 模块一：仪表盘

#### 功能概述
登录后的首页，展示系统全局摘要和最近操作动态。

#### P0 - 必须实现

| 需求编号 | 需求描述 | 验收条件 |
|---------|---------|---------|
| F-D-01 | 展示三个核心统计卡片：应用数、API Key 数、线程池配置总数 | 数字从数据库实时聚合，页面加载后 1s 内渲染 |
| F-D-02 | 展示最近操作记录表格（时间、业务类型、操作类型、对象 ID、操作人） | 按时间倒序展示最新 20 条，支持分页加载更多 |
| F-D-03 | 页面显示数据最后更新时间 | 格式 `yyyy-MM-dd HH:mm:ss` 中文本地化 |

#### P1 - 后续迭代

| 需求编号 | 需求描述 |
|---------|---------|
| F-D-04 | 统计卡片支持点击跳转到对应管理页 |
| F-D-05 | 仪表盘增加"配置变更趋势图"（近 7 天每天变更次数） |
| F-D-06 | 增加"异常线程池"提醒（拒绝率 > 5% 或队列使用率 > 80%） |

---

### 5.2 模块二：线程池配置管理

#### 功能概述
按应用维度管理线程池配置，支持新建、编辑、删除、历史对比和回滚。

#### P0 - 必须实现

| 需求编号 | 需求描述 | 验收条件 |
|---------|---------|---------|
| F-C-01 | 应用选择器：下拉列出所有已注册应用，显示 appId、配置版本号和线程池数量 | 至少支持 200 个应用，下拉加载 < 200ms |
| F-C-02 | 配置列表：展示选中应用下所有线程池的池名称、核心/最大线程数、队列类型(容量)、拒绝策略、存活时间 | 支持 50+ 线程池，表格渲染 < 500ms |
| F-C-03 | 新建线程池：弹窗表单，必填池名称，选填核心线程数(默认 CPU 核数)/最大线程数(默认 2×核心)/队列类型(默认 LinkedBlockingQueue)/队列容量(默认 1024)/拒绝策略(默认 Abort)/存活时间(默认 60s)/时间单位(默认 SECONDS)/线程名前缀 | 提交后列表实时刷新，配置版本号 +1，客户端长轮询收到通知 |
| F-C-04 | 编辑线程池：弹窗预填当前值，修改后保存 | 同 F-C-03 的提交后置行为 |
| F-C-05 | 删除线程池：二次确认弹窗，删除后不可恢复 | 确认后列表实时刷新，删除操作记录审计日志和版本快照 |
| F-C-06 | 配置参数校验：核心线程数 1-1024、最大线程数 >= 核心线程数且 1-1024、队列容量 0-100000、线程名前缀 <= 64 字符、存活时间 >= 0 | 前端即时校验 + 后端 `@Valid` 校验，错误信息友好 |
| F-C-07 | 队列类型支持 6 种：`LINKED_BLOCKING_QUEUE` / `ARRAY_BLOCKING_QUEUE` / `SYNCHRONOUS_QUEUE` / `PRIORITY_BLOCKING_QUEUE` / `DELAY_QUEUE` / `BLOCKING_QUEUE` | 下拉选择 |
| F-C-08 | 拒绝策略支持 5 种：`ABORT` / `CALLER_RUNS` / `DISCARD` / `DISCARD_OLDEST` / `BLOCKED` | 下拉选择 |

#### P1 - 配置历史与回滚

| 需求编号 | 需求描述 | 验收条件 |
|---------|---------|---------|
| F-C-09 | 版本快照：每次配置修改自动创建版本快照，记录完整配置 JSON + 操作人 + 时间戳 | 版本号按 (appId, poolName) 递增，快照保留全量历史 |
| F-C-10 | 配置历史页：展示某个线程池的所有历史版本列表（版本号、时间、操作人），选择任意两个版本进入对比页 | 版本列表按时间倒序 |
| F-C-11 | 配置对比：双栏展示版本 A 和版本 B 的所有配置字段，差异字段高亮标记 | 支持 `poolName / corePoolSize / maximumPoolSize / queueType / queueCapacity / rejectPolicyType / keepAliveTime / timeUnit / allowCoreThreadTimeout / threadNamePrefix / daemon` 共 11 个字段 |
| F-C-12 | 配置回滚：在对比页选择任一目标版本，点击"回滚"按钮，覆盖当前配置 | 回滚本质是创建一条新配置（值为目标版本的值），同时记录新快照（标注回滚操作）并触发客户端通知 |
| F-C-13 | 回滚安全措施：① 回滚前二次确认弹窗 ② 仅回滚被修改的字段，不覆盖未变字段 | 后端校验快照值非空 |

#### P2 - 未来考虑

| 需求编号 | 需求描述 |
|---------|---------|
| F-C-14 | 批量编辑：选中多个线程池，批量修改同一参数（如统一调整队列容量） |
| F-C-15 | 配置模板：预设模板（"高吞吐" / "低延迟" / "IO 密集型"），一键应用 |
| F-C-16 | 配置导入/导出：支持 JSON / YAML 格式的批量导入导出 |

---

### 5.3 模块三：API Key 管理

#### 功能概述
管理接入 client-sdk 的应用凭证，支持创建、查询、更新、重新生成和删除。

#### P0 - 必须实现

| 需求编号 | 需求描述 | 验收条件 |
|---------|---------|---------|
| F-A-01 | 创建 API Key：输入 appId（唯一）、应用名称、描述、过期时间（可选），生成 32 位随机密钥 | 返回明文 Key **仅此一次**，提示用户妥善保存；数据库仅存储 BCrypt 哈希 |
| F-A-02 | API Key 列表：分页展示所有 Key（脱敏显示，不展示哈希值），含 appId、应用名称、状态（启用/禁用）、过期时间、创建时间 | 支持按 appId 搜索 |
| F-A-03 | 获取单个 Key 详情：按 appId 查询，返回脱敏信息 | 不返回 `apiKeyHash` |
| F-A-04 | 更新 Key 元数据：修改应用名称、描述、启用/禁用状态、过期时间 | 不改变密钥值本身 |
| F-A-05 | 删除 Key：删除后客户端使用该 Key 的请求立即返回 401 | 删除操作记录审计日志 |
| F-A-06 | 重新生成 Key：生成新密钥，旧密钥立即失效 | 返回新明文仅此一次；安全原因**不支持回滚**（防止泄露的旧凭证复活） |
| F-A-07 | API Key 认证校验：三级检查 → 存在性 → enabled=true → 未过期 → BCrypt 哈希匹配 | 任一级失败返回 401，错误信息不泄露具体原因（统一 `Invalid API Key`） |

#### P1 - 后续迭代

| 需求编号 | 需求描述 |
|---------|---------|
| F-A-08 | 支持 API Key 绑定 IP 白名单 |
| F-A-09 | 支持 API Key 读写权限分离（当前 appId 内全部权限） |
| F-A-10 | Key 即将过期前 7 天在管理后台展示警告标识 |

---

### 5.4 模块四：操作审计日志

#### 功能概述
记录所有管理操作（API Key 变更、线程池配置变更），支持查询和过滤。

#### P0 - 必须实现

| 需求编号 | 需求描述 | 验收条件 |
|---------|---------|---------|
| F-L-01 | 自动记录操作日志：业务类型（APIKEY / THREADPOOL_CONFIG）、操作类型（CREATE / UPDATE / DELETE / REGENERATE / UPSERT / ROLLBACK）、业务 ID、操作前后内容快照（JSON）、操作人、时间戳 | 使用 `@Async` 异步写入，不阻塞主流程 |
| F-L-02 | 操作日志查询：分页列表，支持按业务类型、操作类型、操作人、业务 ID 过滤 | 单条件或多条件组合过滤 |
| F-L-03 | 日志内容展示：变更内容以 JSON 格式展示，支持格式化查看 | 业务类型和操作类型以中文标签 + 颜色 Badge 展示 |

#### P1 - 后续迭代

| 需求编号 | 需求描述 |
|---------|---------|
| F-L-04 | 日志导出：支持导出为 CSV / Excel |
| F-L-05 | 日志保留策略：超过 N 天的日志自动归档或清理（当前无自动清理） |
| F-L-06 | 敏感字段脱敏：日志 JSON 快照中自动脱敏 API Key 相关字段 |

---

### 5.5 模块五：管理员账号管理

#### 功能概述
SUPER_ADMIN 管理其他管理员账号，所有管理员可修改自己的密码和昵称。

#### P0 - 必须实现

| 需求编号 | 需求描述 | 验收条件 |
|---------|---------|---------|
| F-U-01 | 管理员登录：`POST /api/auth/login`，成功后返回 JWT Token（含 username / role / nickname / iat / exp） | 默认 8 小时过期，密码 BCrypt 验证 |
| F-U-02 | JWT 滑动续期：剩余有效期 < 30 分钟时，响应头 `X-New-Token` 下发热刷新后的 Token | 前端自动检测并替换本地 Token |
| F-U-03 | 修改密码后旧 Token 立即失效：对比 JWT iat（签发时间）与用户 `passwordChangedAt`，若密码变更时间晚于签发时间则拒绝 | 修改密码后所有已登录终端需重新登录 |
| F-U-04 | 管理员列表（仅 SUPER_ADMIN 可见）：分页展示所有管理员，含用户名、昵称、角色、状态、密码最后修改时间 | 不展示密码哈希 |
| F-U-05 | 创建管理员（仅 SUPER_ADMIN）：设置用户名、密码、昵称、角色（SUPER_ADMIN / ADMIN） | 用户名唯一，重复返回 409 错误 |
| F-U-06 | 编辑管理员（仅 SUPER_ADMIN）：修改昵称、角色、启用/禁用状态 | 不能改用户名 |
| F-U-07 | 删除管理员（仅 SUPER_ADMIN）：逻辑删除 | 关联操作日志保留 |
| F-U-08 | 修改自己密码（任意角色）：输入当前密码 + 新密码 | 验证当前密码正确后方可修改 |
| F-U-09 | 修改自己昵称（任意角色）：输入新昵称 | 实时生效 |
| F-U-10 | 安全保护规则：① 默认 `admin` 用户不可删除、不可禁用、角色不可更改 ② 任意管理员不可删除/禁用自己 ③ 任意管理员不可修改自己角色 | 后端校验，违规操作返回 403 |

#### P1 - 后续迭代

| 需求编号 | 需求描述 |
|---------|---------|
| F-U-11 | 密码复杂度策略：最小长度、必须含大小写字母 + 数字 + 特殊字符 |
| F-U-12 | 登录失败锁定：连续 5 次失败锁定 15 分钟 |
| F-U-13 | 登录日志：记录每次登录的 IP、时间、结果（成功/失败） |

---

### 5.6 模块六：客户端 Open API

#### 功能概述
面向 client-sdk 的 REST API，用于配置上报、拉取、订阅和统计上报。

#### P0 - 必须实现

| 需求编号 | 需求描述 | 验收条件 |
|---------|---------|---------|
| F-O-01 | 配置上报：`POST /open/api/thread-pool/config/{appId}/add`，客户端上报单条配置，已存在则跳过（不覆盖） | 返回已有配置或新增配置 |
| F-O-02 | 批量配置上报：`POST /open/api/thread-pool/configs/{appId}/add`，支持批量上报 | 逐条处理，已存在跳过，不存在则新增 |
| F-O-03 | 短轮询拉取：`GET /open/api/thread-pool/config/{appId}/pull`，不传 version 返回全量配置，传 version 且未变更返回 HTTP 304 | 304 响应无 body，节省带宽 |
| F-O-04 | 长轮询订阅：`GET /open/api/thread-pool/configs/{appId}/subscribe?version=N&timeout=30000`，超时范围内等待配置变更，变更立即返回 `{appId, version}`，超时返回 304 | timeout 范围 1-60s，默认 30s；有补偿轮询兜底防止丢变更 |
| F-O-05 | 统计上报：`POST /open/api/thread-pool/stats/report`，批量上报线程池运行时指标（池名、活跃线程数、队列大小、已完成/已提交/异常/拒绝任务数、是否关闭等 15+ 指标） | 异步写入数据库，不阻塞响应 |
| F-O-06 | API Key 认证：所有 `/open/api/thread-pool/**` 路径需要 Header `X-App-Id` + `X-API-Key` | 认证失败返回 401 |
| F-O-07 | 限流：按 `appId` 限流，默认 100 次 / 60 秒（仅 db profile 开启，local profile 关闭） | 通过 `threadpool.admin.rate-limit.enabled` 控制 |

#### P1 - 后续迭代

| 需求编号 | 需求描述 |
|---------|---------|
| F-O-08 | 统计查询 API：管理员可通过 Open API 查询某应用的历史统计数据 |
| F-O-09 | 健康检查端点：`GET /open/api/thread-pool/health` 供客户端探测连通性 |

---

### 5.7 模块七：认证与安全

#### 功能概述
双轨认证体系（管理员 JWT + 客户端 API Key）、密码安全存储、防止常见 Web 漏洞。

#### P0 - 必须实现

| 需求编号 | 需求描述 | 验收条件 |
|---------|---------|---------|
| F-S-01 | 密码 BCrypt 哈希存储，不存储明文 | 使用 `spring-security-crypto` BCryptPasswordEncoder |
| F-S-02 | API Key BCrypt 哈希存储，明文仅在创建/重新生成时返回一次 | 32 位随机字符串，使用 `SecureRandom` |
| F-S-03 | JWT Token 含过期时间，签名密钥可配置 | 默认密钥 `threadpool-admin-jwt-default-secret-please-change-in-production`，生产环境强制更换 |
| F-S-04 | 认证拦截器分离：管理员认证拦截 `/api/**`，API Key 认证拦截 `/open/api/thread-pool/**` | 路径不交叉，互不干扰 |
| F-S-05 | ThreadLocal 上下文清理：请求结束后 `afterCompletion` 清除 `AdminUserContext` | 防止内存泄漏和跨请求数据污染 |
| F-S-06 | 全局异常处理映射：404 → `ResourceNotFoundException`，401 → `AuthenticationException`，403 → `AuthForbiddenException`，409 → `ResoureAlreadyExistsException`，304 → `ResourceNotModifiedException`（真 HTTP 304 无 body），400 → 字段级校验错误 | 统一 `ApiResponse` 封装，`code=0` 表示成功 |

#### P1 - 后续迭代

| 需求编号 | 需求描述 |
|---------|---------|
| F-S-07 | CORS 配置可定制（当前无跨域配置） |
| F-S-08 | 请求日志记录（访问 IP、路径、耗时） |
| F-S-09 | XSS / CSRF 防护（管理后台为 SPA，当前无 CSRF Token 机制） |

---

### 5.8 模块八：系统架构能力

#### P0 - 必须实现

| 需求编号 | 需求描述 | 验收条件 |
|---------|---------|---------|
| F-T-01 | 双 Profile 部署：`local` 模式使用 H2 内存库 + ConcurrentHashMap 缓存（零外部依赖），`db` 模式使用 MySQL + Redisson 分布式缓存 | Spring Profile 切换，local 为默认值 |
| F-T-02 | 优雅停机：所有异步线程池注册 Shutdown Hook，等待 30s 完成进行中任务 | `ExecutorUtils.shutdown()` 统一管理 |
| F-T-03 | 前端 SPA：纯静态 HTML + CSS + JS，无前端构建工具链，直接由 Spring Boot 静态资源服务 | 页面包括 index.html(仪表盘)、thread-pools.html(配置管理)、config-diff.html(配置对比) |
| F-T-04 | 缓存层抽象：`CacheService` 接口统一 local `ConcurrentHashMap` 和 remote `Redisson RMap` | `Storage` 实现对缓存类型透明，`CachedStorageSupport<T>` 基类封装原子 `compute()` 操作 |
| F-T-05 | 配置变更监听器：`MyBatisConfigStorage` 保存配置后通过 `ConfigChangeListenerManager` 异步通知所有订阅者 | 监听器管理使用 `CopyOnWriteArrayList` 保证并发安全，定时清理过期监听器 |

---

## 6. 非功能需求

### 6.1 性能

| 指标 | 目标值 | 备注 |
|------|--------|------|
| 管理后台页面首屏加载 | < 2s | 纯静态资源，无前端构建 |
| API 响应时间（P95） | < 500ms | 不含数据库网络延迟 |
| 配置查询（单应用 50 线程池） | < 200ms | 缓存命中时 |
| 长轮询并发连接数 | >= 500 个应用同时订阅 | `subscriptionExecutor` 线程池支撑 |
| 统计上报吞吐量 | >= 1000 TPS | 异步批量写入数据库 |

### 6.2 可用性

| 指标 | 目标值 |
|------|--------|
| 管理后台可用性 | 99.5%（允许单实例重启期间不可用） |
| 配置数据持久性 | 数据库保证，配置快照不丢失 |
| 客户端配置拉取 | Open API 独立于管理后台，不受管理后台故障影响 |

### 6.3 安全性

| 要求 | 实现方式 |
|------|---------|
| 密码存储 | BCrypt 哈希（不可逆） |
| API Key 存储 | BCrypt 哈希，明文仅在创建/重新生成时返回一次 |
| 传输安全 | 生产环境强制 HTTPS（由反向代理/网关保证，应用层不做 TLS 终止） |
| JWT 签名密钥 | 外部化配置，生产环境必须更换默认值 |
| 认证失败信息 | 统一模糊错误信息（不区分"用户不存在"和"密码错误"） |

### 6.4 可扩展性

| 要求 | 说明 |
|------|------|
| 水平扩展 | db profile 下可部署多实例（Redisson 分布式缓存 + MySQL 共享存储），但长轮询订阅为单实例内存管理，多实例需加 Redis Pub/Sub 广播 |
| 存储实现可替换 | 所有 Storage 接口（`ConfigStorage` / `ApiKeyStorage` / `AdminUserStorage` / `ConfigSnapshotStorage` / `StatsStorage`）可独立替换实现 |

### 6.5 可观测性

| 要求 | 说明 |
|------|------|
| 操作审计 | 所有管理操作记录完整审计日志到 `operate_log` 表 |
| 数据库慢查询 | MyBatis-Plus `StdOutImpl` 日志（开发环境），生产建议切换为 SLF4J |
| 健康检查 | 依赖 Spring Boot Actuator（当前未显式集成） |

---

## 7. 成功指标

### 7.1 北极星指标

**配置变更从"修改完成"到"客户端生效"的端到端延迟（P95）< 35s**

这是衡量系统核心价值——"动态调参实时性"的关键指标。

### 7.2 驱动指标

| 指标 | 基线 | 目标（3 个月） | 测量方式 |
|------|------|---------------|---------|
| 管理后台月活管理员数 | N/A | ≥ 5 人 | 登录日志统计 |
| 托管应用数 | 当前代码中 samples 应用数 | ≥ 20 | `thread_pool_config_app` 表 `COUNT(DISTINCT app_id)` |
| 托管线程池总数 | 当前代码中配置数 | ≥ 100 | `thread_pool_config` 表 `COUNT(*)` |
| 月均配置变更次数 | N/A | ≥ 50 | `operate_log` 表 `operate_type IN ('UPDATE','CREATE','UPSERT')` 按月计数 |
| 配置回滚次数占比 | N/A | < 10% | 回滚次数 / 总变更次数（回滚率越低说明首次配置质量越高） |
| 客户端长轮询订阅成功率 | N/A | > 99% | 监控 DeferredResult 超时 vs 正常返回比例 |

### 7.3 健康指标

| 指标 | 告警阈值 | 说明 |
|------|---------|------|
| API 响应 P99 延迟 | > 2s | 数据库或缓存异常 |
| Open API 认证失败率 | > 10% | 大量无效 Key 或攻击 |
| 长轮询连接数 | > 800 | 接近 `subscriptionExecutor` 最大线程数 |
| 统计上报写入延迟 | > 10s | 数据库写入阻塞 |
| 管理员登录失败率 | > 20% | 暴力破解风险或密码遗忘 |

---

## 8. 技术架构概要

### 8.1 模块定位

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
                    │   AdminUser               │
                    └──────────┬──────────────┘
                               │
         ┌─────────────────────┼─────────────────────┐
         │                     │                     │
  ┌──────▼──────┐   ┌─────────▼────────┐   ┌────────▼────────┐
  │  Admin Auth  │   │  Config Storage  │   │  Open API       │
  │  (JWT+BCrypt)│   │  (MyBatis+Cache) │   │  /open/api/**   │
  └──────────────┘   └────────┬────────┘   │  (API Key Auth) │
                              │             └─────────────────┘
                    ┌─────────▼────────┐
                    │   Cache Service   │
                    │  Local: CHM       │
                    │  Remote: Redisson  │
                    └────────┬──────────┘
                             │
                    ┌────────▼──────────┐
                    │      MySQL        │
                    │  7 tables         │
                    └───────────────────┘
```

### 8.2 数据库设计（7 张表）

| 表名 | 核心字段 | 唯一约束 |
|------|---------|---------|
| `admin_user` | username, password_hash, role, nickname, password_changed_at | `username` UNIQUE |
| `api_key` | app_id, api_key_hash, app_name, enabled, expire_time | `app_id` UNIQUE |
| `thread_pool_config_app` | app_id, version | `app_id` UNIQUE |
| `thread_pool_config` | app_id, pool_name, core/max/queue/reject/... | `(app_id, pool_name)` UNIQUE |
| `thread_pool_stats` | app_id, pool_name, 15+ 指标字段, collect_time | 无（时序数据，按 collect_time 范围查询） |
| `operate_log` | biz_type, operate_type, content(JSON), operator, biz_id | 无（按条件过滤分页） |
| `config_history` | app_id, pool_name, version, config_value(JSON), operator | 唯一索引 `(app_id, pool_name, version)` |

所有表使用逻辑删除（`deleted` 字段 0/1）。

### 8.3 配置项参考 （`application.yml`）

```yaml
threadpool:
  admin:
    auth:
      enabled: true                        # 是否启用管理员认证
      username: admin                      # 默认管理员用户名
      password: changeme                   # 默认密码（生产必须更换）
      secret: <jwt-signing-key>           # JWT 签名密钥（生产必须更换）
      token-expire-minutes: 480           # Token 过期时间（8小时）
      renew-threshold-minutes: 30         # 滑动续期阈值
    rate-limit:
      enabled: false                       # 限流开关（db profile 下默认开启）
    subscription:
      core-pool-size: 10                   # 长轮询线程池大小
  app-auth-enabled: true                   # Open API 认证开关
```

---

## 9. 风险与缓解

| 风险 | 严重度 | 影响 | 缓解措施 |
|------|--------|------|---------|
| ~~**远程模式启动失败**：`storage.type=redis-mysql` 时 `ConfigHistoryStorage` Bean 未注册，Controller 构造注入失败~~ | 🔴 高 | 生产集群无法启动 | ✅ **已识别并计划修复**（REFACTORING_PROPOSAL P0-3） |
| **长轮询多实例问题**：多实例部署时订阅者仅注册在单个实例内存中 | 🟠 中 | 配置变更仅通知同实例订阅者 | 生产推荐单实例部署；多实例场景需引入 Redis Pub/Sub 广播机制 |
| **API Key 泄露**：明文 Key 仅展示一次，但无自动过期通知 | 🟠 中 | Key 过期后客户端调用失败但无预警 | v2 计划增加过期前 7 天提醒 |
| **配置回滚安全**：无"预览回滚效果"功能 | 🟡 低 | 回滚到错误版本可能引发生产事故 | 当前有二次确认弹窗 + 对比页面展示差异 |
| **管理员误删自己**：安全规则已覆盖 | 🟢 低 | — | 后端校验 `admin` 用户不可删除，任意管理员不可删除自己 |
| **统计上报数据膨胀**：无自动清理策略 | 🟡 低 | 长期运行后 `thread_pool_stats` 表过大 | 建议生产配置定时任务按 `collect_time` 清理历史数据 |
| **JWT 默认密钥泄露**：默认值在源码中可见 | 🟠 中 | 使用默认密钥部署生产环境无安全性 | 启动文档和配置注释中显式警告，建议加启动时校验 |
| **无 CSRF 保护**：管理后台为 SPA + JWT Header 认证 | 🟡 低 | 传统 CSRF 攻击对此认证模式影响有限 | JWT 存储在内存/localStorage 而非 Cookie，不自动随请求发送 |

---

## 10. 开放问题

| # | 问题 | 负责人 | 类型 | 状态 |
|---|------|--------|------|------|
| Q1 | 多实例部署时，长轮询订阅如何跨实例通知？是否需要引入 Redis Pub/Sub？ | 架构 | 非阻塞 | 待决策 |
| Q2 | 统计上报数据保留策略？默认保留多少天？是否需要自动归档？ | 产品 | 非阻塞 | 待定义 |
| Q3 | 是否需要集成 Spring Boot Actuator 暴露 `/actuator/health` 和 `/actuator/metrics`？ | 工程 | 非阻塞 | 建议增加 |
| Q4 | 前端是否需要迁移到现代框架（React/Vue）以支撑更复杂的交互？ | 工程 | 非阻塞 | 当前纯 HTML 可满足 v1 |
| Q5 | 客户端 SDK 重连策略？当 admin-server 重启期间，客户端如何处理配置缺失？ | 工程 | 非阻塞 | — |
| Q6 | 是否需要支持配置灰度发布（按 appId 百分比逐步推送）？ | 产品 | 非阻塞 | v2 评估 |

---

## 11. 附录

### A. 完整 API 端点清单

#### 管理后台 API（JWT 认证）

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/auth/login` | 管理员登录 |
| `POST` | `/api/auth/logout` | 管理员登出 |
| `POST` | `/api/api-keys` | 创建 API Key |
| `GET` | `/api/api-keys` | 分页查询 API Key 列表 |
| `GET` | `/api/api-keys/{appId}` | 查询单个 API Key |
| `PUT` | `/api/api-keys/{appId}` | 更新 API Key 元数据 |
| `DELETE` | `/api/api-keys/{appId}` | 删除 API Key |
| `POST` | `/api/api-keys/{appId}/regenerate` | 重新生成 API Key |
| `GET` | `/api/thread-pool/configs` | 列出所有应用摘要 |
| `GET` | `/api/thread-pool/configs/{appId}` | 获取应用全量配置 |
| `GET` | `/api/thread-pool/configs/{appId}/{poolName}` | 获取单个配置 |
| `POST` | `/api/thread-pool/configs/{appId}` | 批量保存配置 |
| `POST` | `/api/thread-pool/configs/{appId}/{poolName}` | 保存单个配置 |
| `POST` | `/api/thread-pool/config/{appId}/add` | 添加单条（存在不覆盖） |
| `POST` | `/api/thread-pool/configs/{appId}/add` | 批量添加（已存在跳过） |
| `DELETE` | `/api/thread-pool/configs/{appId}` | 清空应用配置 |
| `DELETE` | `/api/thread-pool/configs/{appId}/{poolName}` | 删除单个配置 |
| `GET` | `/api/thread-pool/configs/{appId}/version` | 获取配置版本号 |
| `GET` | `/api/thread-pool/configs/{appId}/{poolName}/snapshots` | 获取配置快照历史 |
| `POST` | `/api/thread-pool/configs/{appId}/{poolName}/rollback` | 回滚到指定版本 |
| `GET` | `/api/dashboard/summary` | 仪表盘聚合数据 |
| `GET` | `/api/operate-logs/list` | 分页查询操作日志 |
| `GET` | `/api/admin-users` | 管理员列表（仅 SUPER_ADMIN） |
| `POST` | `/api/admin-users` | 创建管理员（仅 SUPER_ADMIN） |
| `PUT` | `/api/admin-users/{username}` | 更新管理员（仅 SUPER_ADMIN） |
| `DELETE` | `/api/admin-users/{username}` | 删除管理员（仅 SUPER_ADMIN） |
| `PUT` | `/api/admin-users/me/password` | 修改自己密码 |
| `PUT` | `/api/admin-users/me/nickname` | 修改自己昵称 |

#### 开放 API（API Key 认证）

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/open/api/thread-pool/config/{appId}/add` | 上报单条配置 |
| `POST` | `/open/api/thread-pool/configs/{appId}/add` | 批量上报配置 |
| `GET` | `/open/api/thread-pool/config/{appId}/pull` | 短轮询拉取配置 |
| `GET` | `/open/api/thread-pool/configs/{appId}/subscribe` | 长轮询订阅变更 |
| `POST` | `/open/api/thread-pool/stats/report` | 上报运行时统计 |

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
```

### C. 重构改进方向

基于代码审查发现的已知问题（详见 `REFACTORING_PROPOSAL.md`）：

1. **[P0] 清理死代码**：`controller/dto/request/` 和 `controller/dto/response/` 下 5 个类全仓库无引用
2. **[P0] 修复远程模式启动 Bug**：`storage.type=redis-mysql` 时 `ConfigHistoryStorage` Bean 缺失导致启动失败
3. **[P0] 统一 Web DTO 模型**：存在两套请求/响应模型（`pojo/cmd` vs `controller/dto`），命名冲突
4. **[P1] 重建应用服务层**：Controller 直接操作 Storage 绕过 Service，业务逻辑散落
5. **[P1] 消除 dao 四层冗余**：`mapper/rep/service/storage` 职责重叠，3 个空壳 Rep
6. **[P2] 模型爆炸**：同一概念存在 4-5 个几乎相同的类（Entity/Dto/Bean/Cmd）

---

> **文档维护**：本文档基于 `admin-server` 模块 v0.0.1-SNAPSHOT 代码逆向分析撰写，随功能迭代同步更新。
