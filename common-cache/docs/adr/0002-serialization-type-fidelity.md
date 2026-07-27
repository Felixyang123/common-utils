# 缓存序列化：用类型保真换反序列化安全

## Status

accepted

## Context

原实现用 Jackson `activateDefaultTyping(NON_FINAL)` 序列化缓存值和同步消息——在 JSON 里写入 `@class` 全限定类名，反序列化时自动还原任意类型。这存在两个问题：一是**反序列化 RCE 攻击面**（CVE 已知，`@class` 可指定任意 gadget 类）；二是**类重命名后存量数据不可读**（FQCN 耦合了存储格式与类全名）。

企业级安全评审（H6）将其列为 HIGH，要求移除 `activateDefaultTyping`。

## Decision

用自定义 `CachePayloadRedisSerializer` 替代，采用**信封格式**：外层类型（`CacheWrapper` / `CacheSyncMessageImpl`）由白名单包或注册别名还原，内层载荷（`CacheWrapper<T>` 的 `data`）的类型记录在信封 `dt` 字段，同样按白名单/别名判定；未命中的类型**降级为 `Map` 并记 warn**，绝不抛异常杀死订阅线程。

类型还原路径：`dt` 命中已注册别名 → 用该类型还原；`dt` 以 `cache.serializer.allowed-packages` 中某个前缀开头 → `Class.forName` 还原；否则 → 降级 `Map`。

## Considered Options

- **保留 Jackson default-typing 但加 `PolymorphicTypeValidator` 白名单**：改动最小，但 `@class` 元数据仍是向量（validator 被绕过的风险）；存量数据与类名耦合问题不变。拒绝。
- **信封格式 + 内层统一降级为 Map**：最安全，但复杂缓存值（DTO/记录）读回来是 `Map`，调用方 `cache.get()` 强转业务类型时 `ClassCastException`。对只缓存标量的场景足够，但不满足通用缓存库的定位。拒绝。
- **Protobuf / 序列化框架**：类型保真好，但引入重型新依赖，与现有 Jackson 生态不兼容。拒绝。

## Consequences

- **业务方必须把领域包加入 `cache.serializer.allowed-packages`**，否则其 DTO 会被降级为 `Map`。默认白名单仅含 `com.lezai`（库自身）。这是新增的集成门槛，需在文档和 starter 示例中明确说明。
- **`registerAlias(Class<?> type)`** 程序化注册别名可用于抗类重命名（逻辑名替换 FQCN），但当前实现用的是 FQCN（`type.getName()`），类重命名后别名失效。若需要严格的重命名安全，别名应改为逻辑名（字符串）而非 FQCN——留作未来增强。
- **同步消息不受影响**：`CacheSyncMessageImpl` 字段全是标量（String/Long），信封还原后类型完整。
