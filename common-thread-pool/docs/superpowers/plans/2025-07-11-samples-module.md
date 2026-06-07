# Samples Module Implementation Plan

> **For agentic workers:** Already implemented — 2 sub-modules created reflecting modes actually supported by the code.

**Goal:** Create Spring Boot sample applications demonstrating supported thread pool modes.

**Architecture:** `samples/` Maven aggregator with 2 sub-modules, each a standalone Spring Boot app.

**Tech Stack:** JDK 21, Spring Boot 3.x, Maven, thread-pool-spring-boot-starter 0.0.1-SNAPSHOT

---

### Created Files

| File | Description |
|------|-------------|
| `pom.xml` (root) | Added `<module>samples</module>` |
| `samples/pom.xml` | Aggregator POM, `pom` packaging, 2 sub-modules |
| `samples/sample-local/pom.xml` | LOCAL mode, port 8081 |
| `samples/sample-local/src/main/resources/application.yml` | 3 pool definitions |
| `samples/sample-local/.../LocalApplication.java` | `@SpringBootApplication @EnableThreadPool` |
| `samples/sample-local/.../service/DemoService.java` | `@AsyncThreadPool`, `@CreateThreadPool`, programmatic, stats |
| `samples/sample-local/.../controller/DemoController.java` | 5 REST endpoints |
| `samples/sample-cs-client/pom.xml` | CS client mode, port 8083 |
| `samples/sample-cs-client/src/main/resources/application.yml` | `thread.pool.remote.*` config |
| `samples/sample-cs-client/.../CsClientApplication.java` | `@SpringBootApplication @EnableThreadPool` |
| `samples/sample-cs-client/.../service/DemoService.java` | Async task, status, stats |
| `samples/sample-cs-client/.../controller/DemoController.java` | 4 REST endpoints |

### Running

```sh
# LOCAL mode
cd samples/sample-local
mvn spring-boot:run
curl http://localhost:8081/demo/order/123
curl http://localhost:8081/demo/stats

# CS client mode (needs admin-server running)
cd samples/sample-cs-client
mvn spring-boot:run
curl http://localhost:8083/demo/status
```
