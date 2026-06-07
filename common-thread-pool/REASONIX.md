# common-thread-pool

Dynamic thread pool management component (Spring Boot) supporting LOCAL / FILE / CS / NACOS configuration modes.

## Stack

- **Java 21** — configured in root `pom.xml` via `<java.version>21</java.version>`; `JAVA_HOME` = `C:\Users\wangyang\.jdks\ms-21.0.8`
- **Maven 3.9.15** — multi-module (`pom.xml` at root), no Gradle or wrapper; `~/.m2/settings.xml` configured for local `D:\tool\maven\rep` with internal Nexus mirror
- **Spring Boot 3.x** — web, AOP, auto-configuration starters
- **MyBatis-Plus** — DAO layer in `admin-server`, entity→mapper→repository pattern
- **Redisson** — Redis-backed storage for CS mode
- **MapStruct** — DTO ↔ entity converters in `admin-server` (needs annotation processor)
- **FastJSON2** — JSON serialization across all modules
- **Lombok** — `@Data`, `@Builder`, `@Slf4j`, `@RequiredArgsConstructor` throughout
- **Guava** — used in `core` module

## Layout

| Directory | Purpose |
|-----------|---------|
| `core/` | Domain models (`ThreadPoolConfig`, `ThreadPoolStats`), enums (`QueueType`, `RejectPolicyType`) |
| `admin-server/` | Management server: Spring Boot app, REST controllers, MyBatis DAO, storage backends (Redis, local file, remote) |
| `client-sdk/` | Client library: annotations (`@CreateThreadPool`, `@AsyncThreadPool`, `@EnableThreadPool`), aspects, auto-config, pool manager |
| `thread-pool-spring-boot-starter/` | Spring Boot starter: wraps client-sdk + core as an auto-configured dependency |
| `samples/` | Example apps: `sample-local` (LOCAL mode, port 8081), `sample-cs-client` (CS client, port 8083) |

## Commands

```sh
mvn clean install          # full build (all modules)
mvn package                # produce JARs per module
mvn compile                # compile only
```

No test / lint / format / typecheck scripts exist.

## Conventions

- **Package root**: `com.lezai.threadpool` across all modules
- **Layer packages**: `bean/` (models), `config/` (auto-config), `controller/`, `service/`, `dao/` (entity/mapper/rep), `storage/`, `enums/`, `pojo/` (cmd/dto/query/resp)
- **Lombok on models**: `@Data` + `@Builder` on beans; `@Slf4j` + `@RequiredArgsConstructor` on services
- **Declarative thread pool**: `@CreateThreadPool` annotation on methods creates + submits async work
- **CS mode storage**: interface-driven — `ConfigStorage` / `StatsStorage` with Redis, local-file, and concurrent-map implementations
- **API key auth**: interceptor-based (`ApiKeyAuthInterceptor`) with rate limiting (`RateLimitInterceptor`)

## Watch out for

- **No tests exist** — no `src/test` directories, no JUnit/surefire dependency in any POM. Changes must be verified manually.
- **MapStruct converters** in `admin-server` require the `mapstruct-processor` annotation processor (declared `<scope>provided</scope>`). IDE annotation processing must be enabled or `mvn compile` generates the mapper implementations.
- **`target/` directories** are build output — ignore during exploration.
- **Parent POM is external** — the effective Java/Spring version and dependency management come from `com.lezai:common-utils:0.0.1-SNAPSHOT` (not in this repo).
