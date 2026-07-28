# CONTEXT — idempotent 幂等组件词表

> 本文件只是词表（glossary）。不记录实现细节、不作为规格说明。
> 相关决策见 `adr/`。

## 部署与形态

- **多实例为主（Multi-instance First）**：组件的目标运行形态按分布式多实例设计与验证；Local 实现（锁与存储）仅作为单实例/测试的**降级路径**。
- **降级路径（Degradation Path）**：Local 锁 / Local 存储的定位——仅用于单实例部署或测试，多实例下不保证幂等。默认配置指向 Redis，Local 需显式选择。

## 锁与租约

- **锁即租约（Lock-as-Lease）**：锁的存在性代表业务执行者的存活性。处理 PROCESSING 记录时，以「锁是否仍被持有」判定执行者生死；锁已死即视为执行者崩溃。
- **接管（Takeover）**：发现 PROCESSING 记录但锁已死亡时，新请求获取锁、将该记录转为 FAILED 并重新执行业务的路径。
- **看门狗（Watchdog）**：锁持有期间由后台线程按 leaseTime/3 周期自动续期的机制（common-lock 的 WatchDogExecutor）。
- **崩溃窗口（Crash Window）**：执行者宕机到锁自然死亡（看门狗停止续约）之间的时长，约等于锁 leaseTime（默认 60s）。此窗口内同 key 请求按原策略 fail-fast 或重试。

## 记录与生命周期

- **幂等记录（Idempotent Record）**：key 的执行事实，状态机为 PROCESSING → SUCCEEDED / FAILED。
- **Rolling TTL**：记录有效期从「状态最后一次更新」起算（成功/失败落库时重设 expire_time），三种存储语义一致。
- **毒 key（Poison Key）**：业务恒失败的幂等 key；每次请求都重执行会造成重试放大，靠 fail_count 冷却防御。
- **失败冷却（Fail Cooldown）**：FAILED 记录重执行次数达 maxFailRetryCount（默认 3）后不再执行、直接返回上次错误的机制；key 经 TTL 过期或运维清除后恢复。

## 故障与降级

- **fail-close**：存储故障时拒绝请求（映射 503 + Retry-After），宁错杀不放过。默认策略，源自 ADR-0002「一致性优先于可用性」。
- **fail-open**：存储故障时放行请求（不保证幂等），以可用性为先。ADR-0002 的可选逃生口，需显式配置开启。

## 键

- **幂等键（Idempotent Key）**：`keyPrefix（存储层命名空间）+ prefix（业务前缀）+ parsedKey（SpEL 或生成器输出）`。三层各管一段，禁止重复拼接。

## 接管与冷却

- **冷却（Cooldown）**：FAILED 记录的 `failCount` 达到 `maxFailRetryCount`（默认 3）后，不再重执行业务、直接返回上次错误的机制。key 经 TTL 过期或运维清除后恢复。
- **崩溃接管（Crash Takeover）**：处理 PROCESSING 记录时发现锁已死（执行者崩溃），新请求获取锁并将记录转为 FAILED 后重执行业务的路径。参见 ADR-0004。
