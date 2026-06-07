# Samples 模块设计

## 概述

为 common-thread-pool 创建 2 个独立示例子模块，分别展示代码实际支持的 LOCAL 和 CS 两种运行模式。

## 模块结构

```
samples/                          # Maven 聚合模块
├── pom.xml                       # 继承根 POM，聚合 2 个子模块
├── sample-local/                 # LOCAL 模式示例
└── sample-cs-client/             # CS 客户端示例
```

## 各模块

| 模块 | 演示内容 | 配置要点 |
|------|---------|---------|
| `sample-local` | `@AsyncThreadPool` / `@CreateThreadPool` 注解、编程式 `ThreadPoolManager`、实时统计查询 | `thread.pool.enabled=true`（默认） |
| `sample-cs-client` | CS 客户端连接 admin-server、远程配置拉取、统计上报 | `thread.pool.remote.server.enabled=true` |

## 每个模块的文件

```
sample-xxx/
├── pom.xml
└── src/main/
    ├── java/com/lezai/threadpool/samples/{local,cs}/
    │   ├── XxxApplication.java          # @SpringBootApplication + @EnableThreadPool
    │   ├── service/DemoService.java      # 三种使用方式的演示
    │   └── controller/DemoController.java # REST API 触发
    └── resources/application.yml
```

## 代码约定

- 包名：`com.lezai.threadpool.samples.{local,cs}`
- Lombok（`@Slf4j`、`@RequiredArgsConstructor`、`@Data`）
- 依赖 `thread-pool-spring-boot-starter`

## 根 POM 变更

根 `pom.xml` 新增 `<module>samples</module>`。
