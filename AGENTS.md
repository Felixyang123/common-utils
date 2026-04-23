# AGENTS.md

This file provides guidance to Qoder (qoder.com) when working with code in this repository.

## Project Overview

This is a Java-based utility library containing multiple Spring Boot starter modules for common enterprise functionalities:

- **common-thread-pool**: Dynamic thread pool management with multiple configuration modes
- **common-cache**: Multi-level caching solution with Redis integration
- **common-lock**: Distributed locking with watchdog mechanism
- **common-token-generator**: Token generation and management
- **common-task-flow**: Task orchestration and workflow engine
- **anti-duplicate**: Prevention of duplicate form submissions
- **ratelimit**: Rate limiting implementation
- **idempotent**: Idempotency control for API operations

## Development Commands

### Build and Test
```bash
# Clean and compile
./mvnw clean compile

# Run all tests
./mvnw test

# Run specific test class
./mvnw test -Dtest=ClassNameTest

# Run tests with specific pattern
./mvnw test -Dtest="*Test"

# Package without running tests
./mvnw package -DskipTests

# Install to local repository
./mvnw install
```

### Code Quality
```bash
# Run static analysis
./mvnw checkstyle:check

# Run spotbugs analysis
./mvnw spotbugs:check

# Generate coverage report
./mvnw jacoco:report
```

### Module-specific Operations
```bash
# Build specific module
./mvnw clean install -pl common-thread-pool

# Run tests for specific module
./mvnw test -pl common-cache

# Skip integration tests
./mvnw test -Dgroups="unit"
```

## Architecture Overview

### Core Concepts

1. **Modular Design**: Each functionality is implemented as a separate Maven module that can be used independently
2. **Spring Boot Integration**: All modules provide auto-configuration classes and starter dependencies
3. **Annotation-driven**: Heavy use of annotations for declarative configuration (`@EnableCache`, `@EnableLock`, etc.)
4. **AOP-based**: Cross-cutting concerns implemented using Aspect-Oriented Programming
5. **Strategy Pattern**: Multiple implementation strategies for different deployment scenarios

### Key Architectural Patterns

#### Thread Pool Module
- **Strategy Pattern**: Different configuration sources (LOCAL, FILE, CS, NACOS)
- **Template Method**: Abstract base classes for configuration strategies
- **Factory Pattern**: Dynamic thread pool creation and management
- **Observer Pattern**: Configuration change listeners

#### Cache Module
- **Decorator Pattern**: Multi-level caching with different storage backends
- **Adapter Pattern**: Unified cache interface for different implementations
- **Proxy Pattern**: Cache synchronization between nodes

#### Lock Module
- **Template Method**: Base locking mechanism with extension points
- **Observer Pattern**: Watchdog expiration handling

## Module Dependencies

```
common-utils (root)
├── common-cache
├── common-lock
├── common-token-generator
├── common-task-flow
├── common-thread-pool
│   ├── core
│   ├── admin-server
│   ├── client-sdk
│   └── thread-pool-spring-boot-starter
├── anti-duplicate
├── ratelimit
└── idempotent
```

## Configuration Patterns

### Thread Pool Configuration
```yaml
thread:
  pool:
    enabled: true
    mode: LOCAL  # LOCAL, FILE, CS, NACOS
    default-pool:
      core-pool-size: 10
      maximum-pool-size: 20
      queue-capacity: 1000
    pools:
      - name: order-pool
        core-pool-size: 20
        maximum-pool-size: 50
```

### Cache Configuration
```yaml
cache:
  sync:
    enabled: true
    redis:
      topic: cache-sync-topic
```

### Lock Configuration
```yaml
lock:
  watchdog:
    enabled: true
    interval: 30s
```

## Testing Strategy

### Unit Tests
Located in `src/test/java` for each module, focusing on:
- Individual component functionality
- Business logic validation
- Edge case handling

### Integration Tests
- Database integration tests for storage components
- Redis integration for distributed features
- HTTP client tests for remote services

### Test Utilities
- Mock objects for external dependencies
- Test containers for integration testing
- Custom assertions for domain-specific validations

## Common Development Tasks

### Adding New Modules
1. Create new Maven module in root pom.xml
2. Implement auto-configuration class
3. Add `@Enable*` annotation
4. Create starter dependency
5. Add comprehensive tests
6. Update documentation

### Extending Existing Modules
1. Follow existing patterns and conventions
2. Maintain backward compatibility
3. Add appropriate logging
4. Update configuration properties
5. Extend test coverage

### Debugging Approaches
1. Enable debug logging: `logging.level.com.lezai=DEBUG`
2. Use actuator endpoints for runtime inspection
3. Check thread dumps for concurrency issues
4. Monitor metrics through Micrometer integration

## Technology Stack

- **Java**: 21
- **Spring Boot**: 3.5.3
- **Build Tool**: Maven
- **Testing**: JUnit 5, Mockito
- **External Dependencies**: 
  - Redis (lettuce/jedis)
  - Database drivers (MySQL, PostgreSQL)
  - JSON processing (FastJSON2)
  - HTTP clients (OkHttp)

## Best Practices

1. **Configuration First**: Prefer external configuration over code changes
2. **Fail Fast**: Validate configurations at startup
3. **Graceful Degradation**: Provide sensible defaults
4. **Observability**: Include metrics and health indicators
5. **Documentation**: Keep README and javadocs up to date
6. **Backward Compatibility**: Maintain API stability within major versions

## Troubleshooting Common Issues

### Thread Pool Issues
- Check configuration values (core vs max pool size)
- Monitor queue utilization and rejection counts
- Verify shutdown hooks are properly configured

### Cache Issues
- Validate Redis connectivity and serialization
- Check cache key generation strategies
- Monitor eviction policies

### Lock Issues
- Verify lock TTL and renewal intervals
- Check for deadlocks in distributed scenarios
- Monitor lock acquisition timeouts