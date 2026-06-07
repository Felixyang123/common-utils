# Upgrade to JDK 21

## Summary

Upgrade the project's Java compilation target from the parent POM's default to JDK 21, matching the developer environment `JAVA_HOME` at `C:\Users\wangyang\.jdks\ms-21.0.8`.

## Changes

**Single file**: `pom.xml` (root)

Add `<properties>` block with:

```xml
<java.version>21</java.version>
<maven.compiler.source>21</maven.compiler.source>
<maven.compiler.target>21</maven.compiler.target>
```

| Property | Purpose |
|----------|---------|
| `java.version` | Spring Boot convention — consumed by `spring-boot-maven-plugin` for `-release` flag |
| `maven.compiler.source` / `maven.compiler.target` | Native Maven properties inherited by all submodules |

## Rationale

- Parent POM (`com.lezai:common-utils:0.0.1-SNAPSHOT`, external) defines the default Java version; this override avoids depending on that default
- All four submodules (`core`, `admin-server`, `client-sdk`, `thread-pool-spring-boot-starter`) inherit automatically — no per-module edits
- The codebase already uses `jakarta.validation` (Spring Boot 3.x), which is compatible with JDK 21

## Verification

```sh
mvn compile
```

Should complete with no errors, producing class files at Java 21 bytecode level.

## Risks

None. Pure configuration change; no API or dependency modifications.
