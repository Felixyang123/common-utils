# ADR-0003: 部署形态与默认配置 —— 多实例为主，默认 Redis

## 状态

accepted (2026-07-26)

## 背景与决策

幂等组件的原始默认配置为 `storage=LOCAL` + `lock=LOCAL`。在多实例分布式部署下，Local 锁和存储不跨实例共享，幂等保护静默失效——同 key 请求在不同实例上并发执行业务，等同于没有幂等。

我们决定 **以多实例分布式部署为目标形态**，默认配置改为 `storage=REDIS` + `lock=REDIS`。Local 实现保留，仅作为单实例/测试的降级路径，需显式配置启用。

## 关键理由

| 维度 | 分析 |
|------|------|
| 为什么不保持 LOCAL 默认 | 零配置接入在多实例下静默不安全——不报错、不警告、不生效，比"启动失败"更危险。与 ADR-0002「一致性优先于可用性」原则一致 |
| 为什么不是强制显式配置（无默认值） | 启动即失败破坏面太大（所有零配置用户）；默认 Redis + @ConditionalOnClass 降级到 Local + WARN 是更温和的响亮失败 |
| Local 的定位 | @ConditionalOnMissingBean 兜底 + 启动日志 WARN「仅适用于单实例/测试」。不删除，用于单实例服务和集成测试 |
| @EnableIdempotent 的去留 | 废除。Spring Boot 3 标准自动装配（AutoConfiguration.imports）+ @ConditionalOnBooleanProperty(idempotent.enabled) 门控，无需手动注解。@EnableIdempotent 与自动装配并存会造成「双轨」困惑 |

## 决策影响

- **默认值变更**：`IdempotentProperties.storage` 默认 `StorageStrategy.REDIS`，`lock` 默认 `LockStrategy.REDIS`
- **@EnableIdempotent 删除**：启动类需移除该注解，否则编译失败
- **optional 依赖**：`caffeine`、`common-lock` 标为 `<optional>true</optional>`，Local/Redis Bean 加 `@ConditionalOnClass` 守卫
- **@EnableLock 条件化**：仅在 `lock=REDIS` 时通过内部 @Configuration 类导入，不再硬编码在主配置类上
- **大小写宽松**：`@ConditionalOnProperty(havingValue)` 改为小写（`redis`/`jdbc`），与 Spring Boot 枚举 relaxed binding 一致

## 权衡与后果

- **安全性 vs 兼容性**：默认 Redis 打破零配置用户（需显式配 local 或引入 Redis）。但零配置在多实例下是静默不安全的——响亮失败优于静默失效
- **optional 依赖**：Local 用户需手动加 Caffeine 依赖，Redis 用户需手动加 common-lock 依赖。增加一步配置，但避免了传递依赖污染
- **自动激活**：jar 在 classpath 即生效（matchIfMissing=true）。无 @Idempotent 注解的方法不被切面拦截，静默激活对不用幂等的服务零影响

## 相关文件

- `idempotent/src/main/java/com/lezai/idempotent/config/IdempotentAutoConfiguration.java`
- `idempotent/src/main/java/com/lezai/idempotent/config/IdempotentProperties.java`
- `idempotent/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- `idempotent/pom.xml`
