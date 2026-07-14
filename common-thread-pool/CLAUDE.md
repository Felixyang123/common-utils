# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

`common-thread-pool` is a **dynamic thread pool management component** (Spring Boot) with a **client SDK + management server**. Two operating modes:

- **LOCAL** (default): all pool config comes from local `thread.pool.pools[]`; no server interaction.
- **CS (Client-Server)**: `thread.pool.remote.enabled=true` connects to `admin-server`; config pushed via long-polling subscription + pull.

Declared-before-use semantics: a pool only exists if it was **declared** via an approved channel (`@CreateThreadPool`, `pools[]`, server push in CS mode, or `registerPool`). Referencing an undeclared pool name throws `PoolNotFoundException` — no silent fallback.

## Build & Commands

Parent POM is external (`com.lezai:common-utils:0.0.1-SNAPSHOT`, not in this repo). JDK 21, Maven 3.9+.

```bash
mvn clean install                                   # full multi-module build
mvn -pl core -am clean test                         # core module + its deps
mvn -pl client-sdk -am clean test                   # client-sdk (MockWebServer contract tests)
mvn -pl admin-server -am clean test                 # admin-server
mvn -pl admin-server -am clean test -Dtest=FooTest  # single test class
mvn -pl admin-server -am clean test -Dtest=...#testMethod  # single method
mvn compile                                         # generates MapStruct converter impls (admin-server)
```

No lint / format / wrapper / typecheck scripts exist. No `mvnw`.

## Stack & Tooling

- **Java 21** (`JAVA_HOME` = `C:\Users\wangyang\.jdks\ms-21.0.8`), **Spring Boot 3.x**, **MyBatis-Plus** (admin-server DAO), **Redisson** (Redis storage + distributed lock), **MapStruct** (admin-server DTO/entity converters), **FastJSON2**, **Lombok** (`@Data`/`@Builder`/`@Slf4j`/`@RequiredArgsConstructor`).
- Tests: JUnit 5 + AssertJ + Mockito; HTTP via OkHttp `MockWebServer`; Spring integration via `ApplicationContextRunner`.
- Win-only env note: dev runs on Windows 10; DB password and JWT secret in `application-db.yml` are dev defaults — do not ship to production.

## Module Architecture

Dependency chain: **starter → client-sdk → core** (admin-server depends independently on core only).

### `core` — domain models
- `bean/`: `ThreadPoolConfig`, `ThreadPoolStats`, `ApiResponse`, `ConfigChangeNotification`, `ThreadPoolConfigResp`, `ThreadPoolStatsReport`.
- `enumeration/`: `QueueType`, `RejectPolicyType`.
- `event/`: `ThreadPoolEvent`, `ThreadPoolEventType` (`POOL_CREATED`, `POOL_DESTROYED`, `CONFIG_CHANGED`, `CONFIG_SYNCED`), `ThreadPoolEventPublisher`, `ThreadPoolEventListener` (SPI).
- Events are low-frequency-only by design — task rejection and polling backoff are explicitly **not** events (see CONTEXT.md §运行时事件).

### `client-sdk` — client runtime
- **Wiring** (`client-sdk/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`): registers `ThreadPoolAutoConfiguration`, `ThreadPoolMetricsAutoConfiguration`, `ThreadPoolHealthAutoConfiguration`. Add starter dep → auto-enabled (`thread.pool.enabled=false` to disable).
- `annotation/`: `@AsyncThreadPool(poolName=...)`, `@CreateThreadPool(...)` — declarative pool use/creation.
- `aspect/`: `ThreadPoolAspect`, `CreateThreadPoolAspect`, `AsyncExecutionSupport` — AOP-driven pool capture + async dispatch.
- `manager/`: `ThreadPoolManager` (core registry over `ConcurrentHashMap<String, DynamicThreadPoolWrapper>`), `RemoteConfigSourcePoolManager` (CS-mode subclass). Event publishing **always** outside `compute/ComputeIfAbsent` lambdas — callbacks that read `poolRegistry` inside a map compute lambda would deadlock (JDK 9+ also forbids it).
- `client/`: `ConfigServerClient`, `ConfigPollingService` (long-polling `subscribe` + short-polling `pull` + exponential backoff), `ThreadPoolStatsReporter`.
- `core/`: `DynamicThreadPoolWrapper` (the actual pool wrapper holding `localDeclaredConfig` for unmanage), `ResizableCapacityLinkedBlockingQueue`.
- `metrics/`: `ThreadPoolMetricsBinder` — Micrometer gauges `threadpool.threads.*`, `queue.*`, `tasks.*`, `load.factor`. opt-in via classpath presence of Micrometer.
- `health/`: `ThreadPoolHealthIndicator` (`thread.pool.health.active-thread-threshold` / `queue-usage-threshold`), exposed only when actuator is present.
- `init/`: `ThreadPoolInitializer` registers `default-pool` (cores=CPU, max=2×CPU, queue=128).
- `config/`: `ThreadPoolProperties` (config binding), `ThreadPoolLifecycle` (`SmartLifecycle` — stop remote listener → stop reporter → drain pools with 30s shared deadline two-phase shutdown).
- `event/`: `DefaultEventPublisher`, `LoggingEventListener`.

### `admin-server` — management server (independent process, config tree `threadpool.admin.*`)
- **Entry**: `ThreadPoolAdminServer` (`@SpringBootApplication`).
- **Controllers**: `ThreadPoolConfig` (CRUD + history + version), `ApiKey`, `/api/auth` (login → JWT), `Dashboard`, `Stats`, `AdminUser` (admin-account mgmt), `OperateLog`. Open APIs under `/open/api/...` use `app-id` + `X-API-Key`; management APIs under `/api/**` (except `/api/auth/**`) use admin JWT (`Authorization: Bearer <token>`). The two auth systems are **independent**.
- **DAO**: entity (`BaseEntity` + logical-delete field `deleted`) → MyBatis-Plus mapper → `rep` (repository) layer.
- **Services**: `ConfigAdminService`, `OpenThreadPoolConfigService` (`add` is non-destructive — exists → return 409/unchanged, never overwrites server-tuned values), `SubscriptionService` (long-polling via `DeferredResult`, returns only `{appId, version}` — not full config), `ApiKeyAdminService`, `AdminAuthService`, `OperateLogService`.

#### admin-server storage layer (pluggable, interface-driven)
Bound via `threadpool.admin.storage.type`:
- `local` (default) — local JSON files (`./data/`), zero external deps.
- `redis-mysql` — Redisson `RMap` + MyBatis-Plus, distributed. Enabled by `--spring.profiles.active=db`.

Interfaces: `ConfigStorage`, `StatsStorage`, `ApiKeyStorage`, `AdminUserStorage`, `ConfigSnapshotStorage`. Each has a `*MyBatis*` impl and is wrapped by `CachedStorageSupport` (cache-aside: Caffeine local / Redisson remote via `CacheService`/`Cache`/`CacheConfig`). `SyncLock` is local (`LocalStripedLock`) or distributed (`RedissonSyncLock`), selected automatically. `MyBatisConfigStorage` updates trigger `ConfigChangeListenerManager` so long-polling subscribers get notified.

### `thread-pool-spring-boot-starter` — aggregation
Wraps client-sdk + core as one auto-configured dependency. `pom.xml` only.

### `samples/`
`sample-local` (port 8081, LOCAL mode), `sample-cs-client` (port 8083, CS mode).

## Design Rules (enforce these — they come from ADRs / CONTEXT.md)

1. **Config ownership (CS mode)**: client declares initial values (`pools[]`) + pushes to server; server is runtime authority. Restart must **not** overwrite server-tuned values (`add` semantics, ADR-0001 availability-first).
2. **Server unmanage ≠ pool destroy**: server `deleteConfig` makes client **revert to `localDeclaredConfig`** and keep running; it does NOT destroy the pool. Revert path: REGEX in `RemoteConfigSourcePoolManager` diffs server list vs local registry → missing pool calls `revertToLocalConfig()` (ADR-0004). Pool destroy only via `removePool()` / app shutdown.
3. **Event boundary**: never publish rejection or backoff as `ThreadPoolEvent`; alert on metric rates instead. Never publish from inside `DynamicThreadPoolWrapper` (POJO, holds no publisher).
4. **Min metrics app-tag resolution**: CS: `thread.pool.remote.app-id`; else `spring.application.name`; else `"unknown"`. Deleted pool metrics → sentinel `-1`.
5. **Long-polling response** carries only `{appId, version}`; client pulls full config separately to avoid dirty-writing the client cache during high-frequency server changes.

## Conventions

- Package root `com.lezai.threadpool`. Layer packages: `bean` (models), `config`, `controller`, `service`, `dao` (entity/mapper/rep), `storage`, `enums`, `pojo` (cmd/dto/query/resp), `aspect`, `client`, `manager`, `metrics`, `health`, `init`, `properties`, `core`, `event`, `util`, `utils`. Imports use singular `bean`/`enumeration` (not `beans`/`enums`) — keep that spelling.
- Lombok: `@Data`+`@Builder` on beans, `@Slf4j`+`@RequiredArgsConstructor` on services. Constructor injection, never field injection.
- MapStruct converters (`admin-server/converter/`) need `mapstruct-processor` (`provided`); run `mvn compile` or enable IDE annotation processing — generated impls land under `target/`, do not hand-write them.
- `target/` is build output; ignore during exploration.
- Chinese is the established language for comments, ADRs, CONTEXT, and specs — match surrounding text when editing those docs. Code identifiers remain English.
