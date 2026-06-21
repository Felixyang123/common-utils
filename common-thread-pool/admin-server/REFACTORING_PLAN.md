# admin-server 重构实施计划（REFACTORING_PLAN.md）

> 配套文档：`REFACTORING_PROPOSAL.md`（问题诊断与目标架构）
> 本文档：**可执行**的分阶段实施步骤、验证方法、回滚方案、依赖关系、文件变更清单
> 编写日期：2026-06-21
> 模块根目录：`D:/code/common-utils/common-thread-pool/admin-server`
> 包根：`com.lezai.threadpool`（下文文件路径以模块根的 `src/...` 表示）

---

## 0. 前置基线与通用约定

### 0.1 构建 / 验证命令基线

> 在 `D:/code/common-utils/common-thread-pool` 目录下执行（`-am` 会先编译 admin-server 依赖的 `core` 模块）。

| 用途 | 命令 |
|------|------|
| 仅编译 admin-server（含依赖） | `mvn -pl admin-server -am clean compile` |
| 编译测试代码 | `mvn -pl admin-server -am test-compile` |
| 跑全部测试 | `mvn -pl admin-server test` |
| 跑单个测试类 | `mvn -pl admin-server test -Dtest=ApiKeyControllerTest` |
| 全量构建 | `mvn -pl admin-server -am clean install` |

> ⚠️ **MapStruct 注意**：`converter/` 下接口由注解处理器生成 `*Impl` 到 `target/generated-sources/`。**凡涉及 converter 改名/改方法的步骤，验证必须用 `clean compile`**，否则 IDE 增量编译会残留旧的 `ApiKeyConvertorImpl`，造成"假绿"。

### 0.2 测试现状

模块已有 ~30 个测试类（`src/test/java/com/lezai/threadpool/`），覆盖 controller / service / storage / converter / utils。**每个阶段都以"相关测试类通过"作为验收门槛**。各阶段会标注关联测试类。

### 0.3 版本控制约定（回滚的基础）

1. **每个阶段一个独立 commit**（阶段内子步骤也尽量分 commit），提交信息前缀 `refactor(admin-server): [P0-1] ...`。
2. 回滚原语统一为 `git revert <commit>`（保留历史）或未提交时 `git restore <path>`。
3. 建议在 `dynamic-tp` 之外新开分支 `refactor/admin-server-layering` 操作。
4. 改 converter 后回滚，务必再跑一次 `mvn -pl admin-server clean compile` 清掉残留生成源。

### 0.4 阶段依赖关系总览（哪些可并行）

```
P0.1 删死代码 ─┐
P0.2 改 Convertor 名 ─┼─(并行)─► P1 修启动Bug ─┐
P0.3 删 covert 方法 ─┘                        │
                                              ▼
                              P2 Web DTO 收敛 (依赖 P0.1)
                                              │
                                              ▼
                              P3 重建服务层 (依赖 P2)
                                              │
            P4.2/4.3/4.4 存储去重 ─(可与P3并行)─┘
            P4.1 删空壳Rep / 4.5 模型去重 (建议 P3 之后)
```

| 阶段 | 依赖 | 可与谁并行 | 冲突文件提示 |
|------|------|-----------|------------|
| P0.1 删死代码 | 无 | P0.2 / P0.3 / P1 | 无 |
| P0.2 改 Convertor 名 | 无 | P0.1 / P0.3 | 与 P1 都改 `AdminServerAutoConfiguration.java`（不同行，注意合并） |
| P0.3 删 covert 方法 | 无 | P0.1 / P0.2 / P1 | 无 |
| P1 修启动 Bug | 无 | P0.* | 与 P0.2 都动 `AdminServerAutoConfiguration.java` |
| P2 Web DTO 收敛 | **P0.1** | — | 重建 `controller/dto/` 包，须等 P0.1 删完旧死代码 |
| P3 重建服务层 | **P2** | P4.2/4.3/4.4 | 动 3 个 Controller + Service |
| P4 收敛/去重 | P3（4.1/4.5）；其余可提前 | P3（仅存储类 4.2-4.4） | 4.1 动 Service 与 P3 重叠 |

---

## 阶段 P0：零风险清理

**目标**：删除确证的死代码、修正拼写错误。无行为变更。
**前置依赖**：无。三个子步骤互相独立，可并行/分别提交。

### P0.1　删除 5 个死代码类

**依据**：全仓库无任何文件 import 这 5 个类（已用 grep 核实，匹配仅命中其自身包声明）。Controller 实际用的是 `pojo/cmd/*` 与 `pojo/resp/*`。

**步骤**：删除以下文件，并删除随之变空的目录 `controller/dto/response/`、`controller/dto/request/`、`controller/dto/`。

| 删除文件 |
|----------|
| `src/main/java/com/lezai/threadpool/controller/dto/request/CreateApiKeyRequest.java` |
| `src/main/java/com/lezai/threadpool/controller/dto/request/UpdateApiKeyRequest.java` |
| `src/main/java/com/lezai/threadpool/controller/dto/response/ApiKeyInfoResponse.java` |
| `src/main/java/com/lezai/threadpool/controller/dto/response/CreateApiKeyResponse.java` |
| `src/main/java/com/lezai/threadpool/controller/dto/response/RegenerateApiKeyResponse.java` |

**文件变更清单**：

- 🆕 新增：无
- ✏️ 修改：无
- 🗑️ 删除：上表 5 个 `.java`（+ 3 个空目录）

**验证**：
1. `mvn -pl admin-server -am clean compile` —— 编译通过。
2. grep 确认零残留：搜索 `controller.dto.request`、`controller.dto.response`、`CreateApiKeyRequest`、`UpdateApiKeyRequest` 应无命中。
3. `mvn -pl admin-server test` —— 全绿（这些类无测试引用）。

**回滚**：`git restore src/main/java/com/lezai/threadpool/controller/dto/`（未提交）或 `git revert <P0.1-commit>`。纯删除、无引用，回滚零风险。

---

### P0.2　重命名 `ApiKeyConvertor` → `ApiKeyConverter`

**依据**：拼写应为 `-er`（与 `ThreadPoolConfigConverter`/`ThreadPoolStatsConverter` 一致）。

**步骤**（IDE 重命名 Symbol 最稳，或手工改下列 6 处）：

| 文件 | 行 | 改动 |
|------|----|------|
| `src/main/java/.../converter/ApiKeyConvertor.java` | 13 | 文件名 + 接口名 → `ApiKeyConverter` |
| `src/main/java/.../config/AdminServerAutoConfiguration.java` | 3, 122 | import + 方法参数类型 |
| `src/main/java/.../service/ApiKeyService.java` | 4, 21 | import + 字段类型 |
| `src/main/java/.../storage/remote/RedisMysqlApiKeyStorage.java` | 4, 29, 33 | import + 字段 + 构造参数 |
| `src/test/java/.../service/ApiKeyServiceTest.java` | 5, 34 | import + `@Mock` 字段 |
| `src/test/java/.../storage/remote/RedisMysqlApiKeyStorageTest.java` | 5, 48 | import + `@Mock` 字段 |

> 生成类 `target/generated-sources/.../ApiKeyConvertorImpl.java` 会在 `clean compile` 时按新名重新生成，无需手动处理。

**文件变更清单**：

- 🆕 新增：`converter/ApiKeyConverter.java`（即原文件改名）
- ✏️ 修改：`AdminServerAutoConfiguration.java`、`ApiKeyService.java`、`RedisMysqlApiKeyStorage.java`、`ApiKeyServiceTest.java`、`RedisMysqlApiKeyStorageTest.java`
- 🗑️ 删除：`converter/ApiKeyConvertor.java`（被改名取代）

**验证**：
1. `mvn -pl admin-server -am clean compile`（**必须 clean**，清掉旧 `ApiKeyConvertorImpl`）。
2. `mvn -pl admin-server test -Dtest=ApiKeyServiceTest,RedisMysqlApiKeyStorageTest` —— 通过。

**回滚**：`git revert <P0.2-commit>` 后 `mvn -pl admin-server clean compile`。

---

### P0.3　删除无人调用的 `covertConfigEntity`

**依据**：`ThreadPoolConfigConverter.java:31` 的 `covertConfigEntity(ThreadPoolConfig)` 在 main + test 全仓库**零调用**（grep 仅命中声明行）。属拼写错误且死方法。

**步骤**：删除 `ThreadPoolConfigConverter.java:31` 该方法声明。
（备选：若想保留该映射能力，改名为 `convertConfigEntity`；但既无人用，推荐直接删。）

**文件变更清单**：
- ✏️ 修改：`src/main/java/.../converter/ThreadPoolConfigConverter.java`（删 1 行方法声明）
- 🗑️ 删除：无独立文件

**验证**：`mvn -pl admin-server -am clean compile` + `mvn -pl admin-server test -Dtest=ThreadPoolConfigServiceTest`。

**回滚**：`git revert <P0.3-commit>` + `clean compile`。

---

## 阶段 P1：修复 redis-mysql 模式启动 Bug

**目标**：使 `storage.type=redis-mysql` 时应用能正常启动。
**前置依赖**：无（可与 P0 并行；注意与 P0.2 同改 `AdminServerAutoConfiguration.java`）。
**关联测试**：新增冒烟测试。

**根因**：`localFileApiKeyHistoryStorage()`（`AdminServerAutoConfiguration.java:58-64`）和 `localFileConfigHistoryStorage()`（`:69-75`）带 `@ConditionalOnProperty(havingValue="local", matchIfMissing=true)`，远程模式下不创建；而 `ApiKeyController:36` / `ThreadPoolConfigController:28` 把它们声明为**必填构造依赖** → 远程模式 `NoSuchBeanDefinitionException`。

### P1.1　主修复（推荐，最小改动）：让历史 Bean 在所有模式下默认提供

**步骤**：在 `config/AdminServerAutoConfiguration.java` 中，从两个历史 Bean 方法上**移除** `@ConditionalOnProperty(...havingValue="local"...)`，**保留** `@ConditionalOnMissingBean`。这样任意 `storage.type` 下都会提供文件实现作为兜底（远程模式历史暂走本地文件，可接受）。

```
@Bean
@ConditionalOnMissingBean(ApiKeyHistoryStorage.class)   // 保留
// 删除：@ConditionalOnProperty(... havingValue="local", matchIfMissing=true)
public ApiKeyHistoryStorage localFileApiKeyHistoryStorage() { ... }
```
（`localFileConfigHistoryStorage()` 同样处理）

**文件变更清单**：
- ✏️ 修改：`config/AdminServerAutoConfiguration.java`（删 2 个注解行）
- 🆕 新增：`src/test/java/.../config/RedisMysqlContextWiringTest.java`（见 P1.3）

### P1.2　可选（更彻底，后续迭代）：DB 版历史存储

若要远程模式历史落 MySQL：新增 `storage/remote/MysqlConfigHistoryStorage`、`storage/remote/MysqlApiKeyHistoryStorage`，包装 `OperateLogService`（写）+ 新增 `operate_log` 查询路径（读，需把扁平日志重组为 `ChangeLogEntry`，工作量较大），用 `@ConditionalOnProperty(havingValue="redis-mysql")` 装配。**列为后续任务，不阻塞启动修复。**

- 🆕 新增：`storage/remote/MysqlConfigHistoryStorage.java`、`storage/remote/MysqlApiKeyHistoryStorage.java`、`AdminServerAutoConfiguration` 内 2 个 `@Bean` 方法、`OperateLogRep` 查询方法
- ✏️ 修改：`AdminServerAutoConfiguration.java`、`OperateLogService.java`

### P1.3　验证

新增 `RedisMysqlContextWiringTest`，用 `ApplicationContextRunner` 在 `threadpool.admin.storage.type=redis-mysql`（并提供 mock 的 `RedissonClient`/三个 `*Service`）下断言：`ApiKeyHistoryStorage`、`ConfigHistoryStorage`、`ApiKeyController`、`ThreadPoolConfigController` 四个 Bean 均存在。

```
mvn -pl admin-server test -Dtest=RedisMysqlContextWiringTest
```
> 完整 `@SpringBootTest` 启动需真实 Redis+MySQL（Testcontainers），成本高，作为后续可选。

**回滚**：`git revert <P1-commit>`，恢复原条件注解（单文件、低风险）。

---

## 阶段 P2：Web DTO 收敛为一套

**目标**：消除 `controller/dto` 与 `pojo/{cmd,resp}` 的职责重叠；Web 入参/出参统一一处。
**前置依赖**：**P0.1**（旧 `controller/dto` 死代码须先删除，腾出包名）。
**关联测试**：`ApiKeyControllerTest`。

**方案决策（推荐）**：以 `controller/dto/{request,response}` 为 Web 模型唯一位置（符合常规 Spring 分层，且让 `pojo/cmd` 语义收窄为"服务内部命令"）。
> 备选：保持现状的 `pojo/resp` + `pojo/cmd` 不动，仅靠 P0.1 删死代码即可消重——改动更小但分层语义较弱。团队若偏好最小改动可选此项，本阶段其余步骤跳过。

### P2.1　迁移响应类 `pojo/resp/*` → `controller/dto/response/*`

| 移动 | 从 | 到 |
|------|----|----|
| `ApiKeyInfoResponse` | `pojo/resp/` | `controller/dto/response/` |
| `CreateApiKeyResponse` | `pojo/resp/` | `controller/dto/response/` |
| `RegenerateApiKeyResponse` | `pojo/resp/`（以含 `message` 字段的语义为准，补上该字段） | `controller/dto/response/` |

改动：3 个文件 `package` 声明；`ApiKeyController.java:10-12` 三处 import。

### P2.2　控制器入参 `pojo/cmd/*Cmd` → `controller/dto/request/*Request`

| 重命名+迁移 | 从 | 到 |
|------|----|----|
| `CreateApiKeyCmd` → `CreateApiKeyRequest` | `pojo/cmd/` | `controller/dto/request/`（**保留 `@NotBlank/@Size` 校验注解**） |
| `UpdateApiKeyCmd` → `UpdateApiKeyRequest` | `pojo/cmd/` | `controller/dto/request/` |

改动：`ApiKeyController.java:8-9` import 与 `createApiKey`/`updateApiKey` 形参类型。

### P2.3　`pojo/cmd` 仅保留服务内部命令

保留不动：`ApiKeyUpsertCmd`、`ThreadPoolConfigAppUpsertCmd`、`ThreadPoolConfigUpsertCmd`、`ThreadPoolStatsAddCmd`。

### P2.4　同步测试

更新 `src/test/java/.../controller/ApiKeyControllerTest.java` 的 import 与类型引用。

**文件变更清单**：
- 🆕 新增：`controller/dto/response/{ApiKeyInfoResponse,CreateApiKeyResponse,RegenerateApiKeyResponse}.java`、`controller/dto/request/{CreateApiKeyRequest,UpdateApiKeyRequest}.java`
- ✏️ 修改：`controller/ApiKeyController.java`、`controller/ApiKeyControllerTest.java`
- 🗑️ 删除：`pojo/resp/`（3 文件）、`pojo/cmd/CreateApiKeyCmd.java`、`pojo/cmd/UpdateApiKeyCmd.java`

**验证**：
1. `mvn -pl admin-server -am compile`
2. `mvn -pl admin-server test -Dtest=ApiKeyControllerTest`

**回滚**：`git revert <P2-commit>`（建议 P2 单独成 commit，移动较多，整体回滚最干净）。

---

## 阶段 P3：重建应用服务层、Controller 瘦身（核心）

**目标**：业务逻辑从 Controller 下沉到应用服务；Controller 仅做参数绑定 + 调服务 + 包 `ApiResponse`。消除"Controller 直连 Storage"与"Service 被架空"两条平行路径。
**前置依赖**：**P2**（DTO 位置先定）。建议在 P3 前为三个 Controller 补充特征测试锁定行为。
**关联测试**：`ApiKeyControllerTest`、`ThreadPoolConfigControllerTest`、`OpenThreadPoolConfigControllerTest` + 新增服务测试。

### P3.1　API Key 应用服务

- 🆕 新增 `service/ApiKeyAdminService`：迁入 `ApiKeyController` 中的存在性校验（`:48-51,94-96,146-148,172-173,215-217`）、随机 Key 生成 + Hash（`:54-55`）、`ApiKey` 实体组装（`:58-66,150-156`）、`toApiKeyInfo` 映射（`:188-199`）。注入 `ApiKeyStorage` + `ApiKeyHistoryStorage`。
- ✏️ 改 `ApiKeyController`：删除对 Storage 的直接依赖，改注入 `ApiKeyAdminService`；7 个方法体瘦身为单行委托。
- 建议：把 `ApiKey → ApiKeyInfoResponse` 的映射并入（已改名的）`ApiKeyConverter` 或新增 Web mapper，替代手工 `toApiKeyInfo`。

### P3.2　配置管理应用服务

- 🆕 新增 `service/ConfigAdminService`：迁入 `ThreadPoolConfigController` 的 `ThreadPoolConfigResp` 组装（`:35-39`）、历史查询前的存在性校验（`:143-145,163-166`）、limit 分支逻辑（`:169-173`）。注入 `ConfigStorage`/`StatsStorage`/`ConfigHistoryStorage`。
- ✏️ 改 `ThreadPoolConfigController`：改注入 `ConfigAdminService`，各方法瘦身。

### P3.3　订阅服务（长轮询下沉）

- 🆕 新增 `service/SubscriptionService`：迁入 `OpenThreadPoolConfigController.subscribe`（`:64-134`）的 70 行逻辑（`DeferredResult` 创建、版本比较、`ConfigChangeListener` 匿名类、超时/完成清理、补偿轮询）。
- ✏️ 改 `OpenThreadPoolConfigController.subscribe`：仅创建并返回 `DeferredResult`，逻辑交给服务。

### P3.4　持久化辅助服务正名（消除命名混淆）

- 把 `service/ApiKeyService`、`service/ThreadPoolConfigService`、`service/ThreadPoolStatsService` 重命名为持久化语义（如 `ApiKeyPersistenceService` 等）并归入 `dao`/持久化语义包，与新的应用服务（P3.1-3.3）区分。
- ✏️ 同步改其调用方：`RedisMysqlApiKeyStorage`、`RedisMysqlConfigStorage`、`MysqlStatsStorage` 及对应测试。

**文件变更清单**：
- 🆕 新增：`service/ApiKeyAdminService.java`、`service/ConfigAdminService.java`、`service/SubscriptionService.java`（+ 对应测试 3 个）；可选 Web mapper
- ✏️ 修改：`controller/ApiKeyController.java`、`controller/ThreadPoolConfigController.java`、`open/OpenThreadPoolConfigController.java`、`storage/remote/RedisMysql*Storage.java`、`storage/remote/MysqlStatsStorage.java`、相关测试、`AdminServerAutoConfiguration.java`（若新服务需显式装配）
- 🗑️ 删除/改名：`service/ApiKeyService.java` 等 3 个（改名取代）

**验证**：
1. `mvn -pl admin-server -am clean compile`
2. `mvn -pl admin-server test`（**全量**，重点 3 个 Controller 测试 + 3 个新服务测试全绿）
3. 人工：以 `storage.type=local` 启动，对 README 列出的端点做 1 轮冒烟（创建/查询/更新/删除 Key、保存/拉取/订阅配置）。

**回滚**：P3 改动面大，**强烈建议独立 feature 分支 + 子步骤分 commit**（P3.1/3.2/3.3/3.4 各一）。任一子步骤测试不过即 `git revert` 该 commit；正名（P3.4）与逻辑下沉（P3.1-3.3）解耦，便于单独回退。

---

## 阶段 P4：模型与 DAO 收敛、去重（渐进，可选）

**目标**：消除冗余层与重复代码。**前置依赖**：4.1/4.5 建议在 P3 之后；4.2/4.3/4.4（纯存储类）可与 P3 并行。
**关联测试**：`LocalFile*StorageTest`、`RedisMysql*StorageTest`、`ConcurrentMapStorageTest`。

| 子步骤 | 步骤 | 文件变更 | 验证 | 回滚 |
|------|------|---------|------|------|
| P4.1 删空壳 Rep | 删 `dao/rep/ApiKeyRep.java`、`OperateLogRep.java`、`ThreadPoolStatsRep.java`；调用方（`ApiKeyService`、`OperateLogService`、`ThreadPoolStatsService`）改为直接注入对应 `Mapper` + `Wrappers` 或自身 `extends ServiceImpl`。**保留** `ThreadPoolConfigRep`/`ThreadPoolConfigAppRep`（有真实方法） | 🗑️删3 ✏️改3 Service | `test -Dtest=ApiKeyServiceTest,ThreadPoolStatsServiceTest` | `git revert` |
| P4.2 `saveConfigs` 提默认方法 | 在 `ConfigStorage` 接口加 `default saveConfigs(){ for ... saveConfig() }`；删 `LocalFileConfigStorage:70-74`、`RedisMysqlConfigStorage:72-76` 两处重写 | ✏️改3 | `test -Dtest=LocalFileConfigStorageTest,RedisMysqlConfigStorageTest` | `git revert` |
| P4.3 `validateApiKey` 去重 | 抽到 `ApiKeyStorage` 默认方法或工具类；删 `LocalFileApiKeyStorage:97-125`、`RedisMysqlApiKeyStorage:94-124` 重复实现 | ✏️改3 | `test -Dtest=LocalFileApiKeyStorageTest,RedisMysqlApiKeyStorageTest` | `git revert` |
| P4.4 `getOrBuildFile` 泛型化 | 在 `AbstractLocalFileStorage` 加 `T getOrBuildFile(path, Supplier<T> default)`；4 个 localfile 实现改调用 | ✏️改5 | `test -Dtest=LocalFile*StorageTest` | `git revert` |
| P4.5 评估删中转 DTO | 评估 `ApiKeyDto`/`ThreadPoolConfigDto` 能否由 `Entity↔Bean` 直转，逐个去除 | 视情况 | 全量 `test` | `git revert` |
| P4.6 显式枚举映射 | `ThreadPoolConfigConverter` 中显式声明 `rejectPolicyType` 的 `String↔RejectPolicyType` | ✏️改1 | `clean compile` + `test` | `git revert` |
| P4.7 `StorageType` 正名 | 改名 `StorageEntityType` 或删除（仅日志用） | ✏️改若干 | `clean compile` | `git revert` |

---

## 附录 A：验证命令速查

```sh
# 通用（在 common-thread-pool 目录下）
mvn -pl admin-server -am clean compile        # 编译（converter 改动必用 clean）
mvn -pl admin-server -am test-compile         # 编译测试
mvn -pl admin-server test                     # 全量测试
mvn -pl admin-server test -Dtest=XxxTest      # 单类测试

# 各阶段最小验证集
# P0.1: clean compile + grep 零残留
# P0.2: clean compile + ApiKeyServiceTest,RedisMysqlApiKeyStorageTest
# P0.3: clean compile + ThreadPoolConfigServiceTest
# P1  : RedisMysqlContextWiringTest
# P2  : compile + ApiKeyControllerTest
# P3  : 全量 test + 人工端点冒烟
# P4.x: 对应 *StorageTest / *ServiceTest
```

## 附录 B：回滚通用流程

1. 定位阶段 commit：`git log --oneline --grep "P0-1"`。
2. 回滚：`git revert <commit>`（已推送）或 `git restore <path>`（未提交）。
3. **若该阶段动过 converter**：回滚后必跑 `mvn -pl admin-server clean compile` 清除 `target/generated-sources` 中的旧实现。
4. 重跑该阶段验证命令确认恢复绿。

## 附录 C：建议执行顺序

> 可立即开始且低风险：**P0.1 → P0.2 → P0.3 → P1**（彼此基本独立，注意 P0.2 与 P1 同改一个配置文件）。
> 中风险、需测试护航：**P2 → P3**。
> 渐进优化：**P4**（存储去重 4.2-4.4 可提前与 P3 并行）。
