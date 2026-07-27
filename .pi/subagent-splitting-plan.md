# common-thread-pool 独立拆分计划

## 背景

将 `common-thread-pool` 模块从 `common-utils` 单体仓库中拆分出来，成为一个独立的 Maven 项目。

## 当前状态

- 模块路径: `D:/code/common-utils/common-thread-pool/`
- 子模块: core, admin-server, client-sdk, thread-pool-spring-boot-starter, samples
- 当前 parent: `com.lezai:common-utils:0.0.1-SNAPSHOT`
- 根 pom 提供: Spring Boot BOM (3.5.3), Guava, FastJSON2, Redisson, MyBatis-Plus, MapStruct, OkHttp, Caffeine, Commons Lang3, JJWT 等依赖与版本管理

## 需要完成的工作

### 阶段 1: 摸底分析（scout 并行）

并行启动多个 scout，收集以下信息：

1. **模块结构** - 列出所有目录、源文件、资源文件
2. **依赖分析** - 收集所有子模块 pom.xml 中声明的所有依赖（包括继承自根 pom 的）
3. **配置入口** - spring auto-configuration、spring.factories、application.yml 等

### 阶段 2: 规划（planner）

根据摸底结果，生成实施计划，包括：
- 新项目的独立 pom.xml 结构
- 文件映射方案
- 需要调整的配置项

### 阶段 3: 实施（worker）

一个 worker 在新目录执行所有复制和修改工作。

### 阶段 4: 验证（reviewer）

并行审查 pom.xml 完整性和文件遗漏。

## 预期交付物

- 独立的 Maven 项目目录
- 构建通过（mvn clean compile -f new-project/pom.xml）
- 所有单元测试通过
- 新旧项目对比说明
