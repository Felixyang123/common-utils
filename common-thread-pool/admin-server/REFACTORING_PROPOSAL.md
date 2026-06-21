# admin-server 分层架构重构方案

> 模块：`common-thread-pool-admin-server`
> 分析日期：2026-06-20
> 范围：分层架构梳理（controller / pojo / service / dao / storage）、重复模型与死代码清理、命名规范统一

---

## 一、现状分层概览

当前包结构（`com.lezai.threadpool`）：

```
controller/                 ApiKeyController, ThreadPoolConfigController
  └─ dto/request/           CreateApiKeyRequest, UpdateApiKeyRequest          ← 死代码
  └─ dto/response/          ApiKeyInfoResponse, CreateApiKeyResponse, ...     ← 死代码
open/                       OpenThreadPoolConfigController
pojo/
  ├─ cmd/                   CreateApiKeyCmd, UpdateApiKeyCmd, *UpsertCmd      ← 实际入参
  ├─ dto/                   ApiKeyDto, ThreadPoolConfigDto, *AppDto, ...
  └─ resp/                  ApiKeyInfoResponse, CreateApiKeyResponse, ...     ← 实际出参
bean/                       ApiKey, ThreadPoolAppConfig, ChangeLogEntry, ...  ← 领域模型
service/                    ApiKeyService, ThreadPoolConfigService, OpenThreadPoolConfigService, ...
converter/                  ApiKeyConvertor, ThreadPoolConfigConverter, ...   (MapStruct)
dao/
  ├─ entity/               ApiKeyEntity, ThreadPoolConfigEntity, BaseEntity, ...
  ├─ mapper/               *Mapper (MyBatis-Plus BaseMapper)
  └─ rep/                  *Rep (extends ServiceImpl)
storage/                    ConfigStorage, ApiKeyStorage, StatsStorage (接口)
  ├─ localfile/            LocalFile*Storage
  ├─ remote/               RedisMysql*Storage, MysqlStatsStorage
  └─ listener/             ConfigChangeListenerManager
```

**调用关系实测结论**（已逐文件核实）：

| 入口 | 是否经过 Service | 实际依赖 |
|------|----------------|---------|
| `ApiKeyController` | ❌ 否 | 直接调 `ApiKeyStorage` + `ApiKeyHistoryStorage` |
| `ThreadPoolConfigController` | ❌ 否 | 直接调 `ConfigStorage` + `StatsStorage` + `ConfigHistoryStorage` |
| `OpenThreadPoolConfigController` | ✅ 部分 | `OpenThreadPoolConfigService`，但 `subscribe` 70 行逻辑内联在 Controller |

关键事实：`ApiKeyService` / `ThreadPoolConfigService` / `ThreadPoolStatsService` **没有任何 Controller 调用**，它们只被 `RedisMysql*Storage` / `MysqlStatsStorage`（远程存储实现）引用。也就是说，名义上的"Service 层"实际是远程存储的持久化辅助类，而真正的业务逻辑散落在 Controller 里。

---

## 二、核心问题诊断（按严重度排序）

### 🔴 P0-1　controller/dto 与 pojo/resp 双份响应类（本次重点）

存在**两套**请求/响应模型，命名约定还互相冲突：

| 类名 | `controller/dto/*`（死代码） | `pojo/{cmd,resp}`（实际使用） | 差异 |
|------|------------------------------|-------------------------------|------|
| 创建入参 | `dto/request/CreateApiKeyRequest` ❌ | `cmd/CreateApiKeyCmd` ✅ | 死代码**缺少** `@NotBlank/@Size` 校验注解 |
| 更新入参 | `dto/request/UpdateApiKeyRequest` ❌ | `cmd/UpdateApiKeyCmd` ✅ | 同上 |
| 信息出参 | `dto/response/ApiKeyInfoResponse` ❌ | `resp/ApiKeyInfoResponse` ✅ | 字段完全相同（仅空白差异） |
| 创建出参 | `dto/response/CreateApiKeyResponse` ❌ | `resp/CreateApiKeyResponse` ✅ | 字段完全相同 |
| 重生成出参 | `dto/response/RegenerateApiKeyResponse` ❌ | `resp/RegenerateApiKeyResponse` ✅ | 死代码多一个 `message` 字段 |

**证据**：`ApiKeyController.java:8-12` 仅 import `pojo.cmd.*` 与 `pojo.resp.*`；`controller/dto/` 下 5 个类全仓库无任何 import。

**问题本质**：
1. `controller/dto/request`、`controller/dto/response` 共 **5 个类是纯死代码**，且校验注解版本落后，是埋雷点（有人误用即丢失校验）。
2. 入参用 `Cmd` 后缀放在 `pojo/cmd`，出参用 `Response` 后缀放在 `pojo/resp`，而废弃版本用 `Request`/`Response` 后缀放在 `controller/dto` —— **命名约定精神分裂**。
3. `pojo/resp` 用缩写 `resp`，死代码用全称 `response`，风格不统一。

### 🔴 P0-2　Service 层被架空，存在两条平行代码路径

- `ApiKeyController` / `ThreadPoolConfigController` 绕过 Service 直接操作 Storage，把**业务逻辑写在 Controller 里**：
  - `ApiKeyController.java:48-68` 存在性校验 + 随机 Key 生成 + Hash + 实体组装
  - `ApiKeyController.java:72-79, 188-199` 手工逐字段拼装响应（`toApiKeyInfo` 9 个 setter）
  - `OpenThreadPoolConfigController.java:64-134` `subscribe` 70 行长轮询业务逻辑（监听器匿名类、超时回调、补偿轮询）内联在 Controller
- `ApiKeyService` 等三个"Service"实为远程存储的持久化组件（`Entity` CRUD + Converter），层级定位错误。

**后果**：同一领域操作（如"保存配置"）在 local 模式走 Controller→Storage，在 remote 模式 Storage 又回调 Service，逻辑分叉、难测试、难维护。

### 🔴 P0-3　远程模式启动即失败（潜在 Bug）

`ConfigHistoryStorage` / `ApiKeyHistoryStorage` 的 Bean **只在 `storage.type=local` 时创建**（`AdminServerAutoConfiguration.java:60,71` `havingValue="local", matchIfMissing=true`），**没有 redis-mysql 变体**。

但 `ApiKeyController.java:36`、`ThreadPoolConfigController.java:28` 通过 `@RequiredArgsConstructor` 把它们声明为**必填构造依赖**。

➡️ 当 `storage.type=redis-mysql` 时，这两个 Bean 不存在，两个 Controller 无法实例化，**应用启动直接抛 `NoSuchBeanDefinitionException`**。远程模式的历史记录实际走 `OperateLogService`→`operate_log` 表，但它没有接到 `*HistoryStorage` 接口上，是另一套不互通的 schema。

### 🟠 P1-1　模型爆炸：bean / dto / entity / cmd 近重复

同一概念存在 4~5 个几乎一样的类：

| 概念 | entity | dto | bean(领域) | resp/cmd |
|------|--------|-----|-----------|----------|
| ApiKey | `ApiKeyEntity` | `ApiKeyDto` | `ApiKey` | `CreateApiKeyResponse`/`ApiKeyInfoResponse`/`*Cmd` |
| ThreadPoolConfig | `ThreadPoolConfigEntity` | `ThreadPoolConfigDto` | `ThreadPoolConfig`(core) | `ThreadPoolConfigUpsertCmd` |
| ThreadPoolStats | `ThreadPoolStatsEntity` | `ThreadPoolStatsDto` | `ThreadPoolStats`(core) | `ThreadPoolStatsAddCmd` |

`ApiKeyDto` 与 `ApiKey` 字段几乎一致（仅多 `id`），转换链 `Entity→Dto→Bean` 中 `Dto` 是无实质转换价值的中转层。`ThreadPoolConfigDto` 与 core 的 `ThreadPoolConfig` 字段、类型完全相同，仅少了校验注解。

附带类型隐患：`ThreadPoolConfigEntity.rejectPolicyType` 是 `String`，而 Dto/Cmd/Bean 都是 `RejectPolicyType` 枚举，靠 MapStruct `ReportingPolicy.IGNORE` 静默转换，不可控。

### 🟠 P1-2　dao 四层（mapper/rep/service/storage）职责重叠

- `mapper/`：MyBatis-Plus `BaseMapper`，4 个空接口，仅 `ThreadPoolConfigMapper` 有 2 个 `@Select`。
- `rep/`：`extends ServiceImpl`，其中 `ApiKeyRep`/`OperateLogRep`/`ThreadPoolStatsRep` **三个空壳**（零自定义方法），纯样板。
- `service/`：又直接用 `Wrappers.lambdaQuery()` 绕过 rep 的自定义方法。
- `storage/`：remote 实现回调 service —— `storage` 与 `service` 互相依赖，形成双向耦合。

### 🟡 P2-1　命名不一致

| 位置 | 问题 | 建议 |
|------|------|------|
| `converter/ApiKeyConvertor.java` | `Convertor`（拼写错误，应为 -er） | `ApiKeyConverter` |
| `ThreadPoolConfigConverter.java:31` | 方法 `covertConfigEntity`（漏 n） | `convertConfigEntity` |
| `pojo/resp` vs `controller/dto/response` | `resp` 缩写 vs `response` 全称 | 全局统一 |
| `ThreadPoolConfigConverter` | 方法名 source-first / target-first 混用 | 统一为 `aToB` 风格 |
| `enums/StorageType` | 名为"存储类型"实为"存储实体类型"，且只用于日志 | `StorageEntityType` 或删除 |

### 🟡 P2-2　重复代码

- `getOrBuildFile` 在 4 个 localfile 存储中各写一遍（`LocalFileConfigStorage.java:106` 等）。
- `validateApiKey` 在 `LocalFileApiKeyStorage.java:97` 与 `RedisMysqlApiKeyStorage.java:94` 完全重复。
- `saveConfigs` 在两个 `ConfigStorage` 实现里都是同样的 for 循环 —— 可提为接口 `default` 方法。
- 历史裁剪逻辑（保留最近 N 条）在两个 history 存储中重复。

---

## 三、目标分层架构

确立**单向依赖**：`Controller → Application Service → Storage(持久化抽象) → (Mapper | File)`，模型在层间单向流动。

```
web 层
  controller/            仅做：参数绑定、调 service、包装 ApiResponse
  controller/dto/
    ├─ request/          所有 @RequestBody（带校验注解）—— 唯一入参约定
    └─ response/         所有出参 —— 唯一出参约定
应用服务层
  service/               业务编排：ApiKeyService / ConfigService / StatsService / SubscriptionService
                         （Controller 唯一依赖，封装 storage + listener + 历史）
持久化抽象层
  storage/               ConfigStorage / ApiKeyStorage / ...（local & remote 双实现保留）
  storage/listener/
持久化实现层
  dao/entity, dao/mapper 直接用 MyBatis-Plus，删除空壳 rep
领域模型
  bean/                  ApiKey, ThreadPoolAppConfig, ChangeLogEntry（含业务方法）
转换器
  converter/             MapStruct，统一命名
```

要点：
1. **Web DTO 收敛到一个地方**（推荐 `controller/dto/{request,response}`），删除 `pojo/resp`、`pojo/cmd` 中的"控制器入参"职责重叠部分；`pojo/cmd` 仅保留**服务内部命令**（`*UpsertCmd`）。
2. **Controller 不再碰 Storage**，统一经 Service；`subscribe` 长轮询逻辑下沉到 `SubscriptionService`。
3. 现有 `ApiKeyService` 等持久化辅助类**正名**为持久化层组件（或并入 remote storage），与新的应用服务区分开。

---

## 四、重构步骤（分阶段、可独立交付）

### 阶段 0：零风险清理（半天）
1. **删除死代码**：`controller/dto/request/`、`controller/dto/response/` 共 5 个类。
   - 风险：无（全仓库无引用，已核实）。
2. 修正拼写：`ApiKeyConvertor`→`ApiKeyConverter`，`covertConfigEntity`→`convertConfigEntity`（IDE 重命名，MapStruct 会重新生成实现）。

### 阶段 1：修复远程模式启动 Bug（1 天）
3. 为 `ConfigHistoryStorage` / `ApiKeyHistoryStorage` 补 `redis-mysql` 实现（包装 `OperateLogService`），或在 `AdminServerAutoConfiguration` 为远程模式提供 no-op/db-backed Bean，使两个 Controller 在任意 `storage.type` 下都能装配。
4. 加一个 `@SpringBootTest` 冒烟用例覆盖 `storage.type=redis-mysql` 启动。

### 阶段 2：统一 Web DTO 约定（1~2 天）
5. 选定 `controller/dto/{request,response}` 为唯一 web 模型位置，把实际使用的 `pojo/resp/*` 迁移过去（或反之，取决于团队偏好——**关键是只保留一套**）。
6. `pojo/cmd` 中 `CreateApiKeyCmd`/`UpdateApiKeyCmd`（控制器入参）迁为 `controller/dto/request/*Request`，保留校验注解；`*UpsertCmd`（服务内部命令）留在 `pojo/cmd`。
7. `RegenerateApiKeyResponse` 以含 `message` 的版本为准（语义更完整）。

### 阶段 3：重建应用服务层（3~5 天，核心）
8. 新建/补全 `ApiKeyService`（应用服务语义）、`ConfigAdminService`，把 Controller 里的业务逻辑（存在性校验、Key 生成/Hash、实体组装、响应映射）下沉。
9. `ApiKeyController`/`ThreadPoolConfigController` 改为仅依赖 Service，移除对 Storage/HistoryStorage 的直接依赖。
10. `subscribe` 长轮询逻辑抽到 `SubscriptionService`，Controller 只创建并返回 `DeferredResult`。
11. 原 `ApiKeyService`/`ThreadPoolConfigService`/`ThreadPoolStatsService` 重命名为持久化语义（如 `ApiKeyRepository`/`*PersistenceService`）并移至 dao 包，消除命名混淆。

### 阶段 4：模型与 dao 收敛（3~5 天，可选/渐进）
12. 评估 `ApiKeyDto`/`ThreadPoolConfigDto` 等中转 DTO，能直接 `Entity↔Bean` 的删除中间层。
13. 删除三个空壳 `Rep`（`ApiKeyRep`/`OperateLogRep`/`ThreadPoolStatsRep`），Service 直接注入 `Mapper`。
14. 用 `default` 方法消除 `saveConfigs`、`validateApiKey`、`getOrBuildFile`、历史裁剪等重复代码。
15. 把 `rejectPolicyType` 的 `String↔枚举` 转换在 MapStruct 中显式声明。

---

## 五、优先级与风险

| 优先级 | 事项 | 工作量 | 风险 | 价值 |
|--------|------|--------|------|------|
| **P0** | 删 5 个死代码类 | 0.5d | 无 | 消除误用隐患、统一约定第一步 |
| **P0** | 修复 redis-mysql 启动 Bug | 1d | 低 | 远程模式当前不可用 |
| **P0** | Web DTO 收敛为一套 | 2d | 低 | 直接回应"双份模型"问题 |
| **P1** | 重建应用服务层、Controller 瘦身 | 3-5d | 中 | 根治分层混乱，可测试性 |
| **P1** | Service 正名 / dao 简化 | 3-5d | 中 | 消除 storage↔service 双向耦合 |
| **P2** | 模型去重、命名统一、消重 | 渐进 | 低 | 长期可维护性 |

**前置约束（来自 REASONIX.md）**：当前模块**无任何测试**。强烈建议在阶段 2/3 动手前，先为三个 Controller 与远程存储补特征测试（characterization test），锁定现有行为后再重构。

**建议落地顺序**：阶段 0 → 1 → 2 可立即执行、风险低、独立可交付；阶段 3 是核心但需测试护航；阶段 4 渐进推进。
