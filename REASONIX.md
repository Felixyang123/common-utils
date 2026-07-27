# common-utils — REASONIX.md

## Stack

- **Language:** Java 21
- **Framework:** Spring Boot 3.5.3
- **Build:** Maven (wrapper via `./mvnw`), multi-module POM
- **Database:** MySQL + MyBatis Plus + Redis (Lettuce/Redisson)
- **Key deps:** Caffeine (cache), Lombok, MapStruct, OkHttp, Apache Commons Lang3

## Layout

| Path | Purpose |
|------|---------|
| `common-cache/` | Multi-level cache abstraction (local Caffeine + remote Redis) with sync |
| `common-lock/` | Distributed/local lock abstraction (Redis-based) |
| `common-token-generator/` | Token generation with Redis/local storage |
| `common-task-flow/` | Task flow engine with MySQL-backed execution |
| `anti-duplicate/` | Duplicate submit prevention (interceptor + strategies) |
| `ratelimit/` | Rate limiter (leaky bucket, token bucket, Redis) |
| `idempotent/` | Idempotency enforcement (annotation-driven, AOP) |
| `common-thread-pool/` | Dynamic thread pool (core + admin-server + client-sdk + starter) |
| `thread-pool-spring-boot-starter/` | Standalone Spring Boot starter for thread pool |
| `common-utils-samples/` | Usage samples for sub-modules |
| `spring-boot-samples/` | Additional Spring Boot integration samples |

## Commands

```sh
./mvnw clean install          # Build all modules
./mvnw test                   # Run all tests
./mvnw clean install -DskipTests  # Fast build
```

## Conventions

- **Package:** `com.lezai.*` — each module under its own sub-package (`cache`, `lock`, `token`, `idempotent`, etc.)
- **Tests:** JUnit 5 + Mockito, `@SpringBootTest` for integration tests, `*Test.java` suffix, colocated in `src/test/java/`
- **Lombok:** Heavy use — `@Data`, `@Slf4j`, `@Builder`, `@AllArgsConstructor` on POJOs/DTOs/entities
- **Spring Boot auto-config:** Each library module ships `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- **POJO layout:** `Cmd` (command) / `Dto` / `Resp` (response) / `Entity` separation pattern in thread-pool module
- **No linter/formatter config found** — no checkstyle, spotless, or editorconfig in the repo

## Watch out for

- **Multi-module cross-refs:** `common-cache` depends on `common-lock`; `thread-pool-spring-boot-starter` depends on `common-thread-pool`. Always build from root (`./mvnw`).
- **`CLAUDE.md`** at repo root is empty — no project-specific Claude instructions. `AGENTS.md` also present.
- **`.gitattributes`** enforces LF for `mvnw`, CRLF for `.cmd`.
