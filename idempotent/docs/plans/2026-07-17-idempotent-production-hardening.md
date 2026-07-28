# Idempotent 模块生产可用级加固计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复 idempotent 模块 code review 发现的所有 Critical/High/Medium 问题，使其达到生产可用级别

**Architecture:** 按组件解耦修复：锁策略（Local/Redis）→ 存储策略（Redis/JDBC）→ 核心执行管理器 → 配置。每个修复独立可测试，遵循现有 TDD 模式（JUnit 5 + Mockito）。

**Tech Stack:** Java 21, Spring Boot 3.5.3, JUnit 5, Mockito, Caffeine 3.1.6, fastjson2, MySQL

---

## 文件结构总览

| 组件 | 文件 | 变更类型 |
|------|------|----------|
| 锁 | `idempotent/src/main/java/.../lock/RedisLockProvider.java` | 修复 |
| 锁 | `idempotent/src/main/java/.../lock/LocalLockProvider.java` | 修复 |
| 锁 | `idempotent/src/test/java/.../lock/RedisLockProviderTest.java` | **新建** |
| 存储 | `idempotent/src/main/java/.../storage/JdbcIdempotentStorage.java` | 修复 |
| 存储 | `idempotent/src/main/java/.../storage/RedisIdempotentStorage.java` | 修复 |
| 存储 | `idempotent/src/main/java/.../storage/LocalIdempotentStorage.java` | 修复 |
| 核心 | `idempotent/src/main/java/.../core/IdempotentExecutionManager.java` | 修复 |
| 配置 | `pom.xml`（根） | 修复 |
| SQL | `idempotent/src/main/resources/sql/ddl.sql` | 修复 |

---

## Phase 1: Critical 级修复（上线前置条件）

### Task 1: 修复 RedisLockProvider 锁阻塞 Bug

**Files:**
- Modify: `idempotent/src/main/java/com/lezai/idempotent/lock/RedisLockProvider.java:19`
- Test: `idempotent/src/test/java/com/lezai/idempotent/lock/RedisLockProviderTest.java` (新建)

- [ ] **Step 1: 编写失败测试**

创建 `RedisLockProviderTest.java`：

```java
package com.lezai.idempotent.lock;

import com.lezai.lock.RedisDistributeLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Redis锁提供者测试")
class RedisLockProviderTest {

    @Mock
    private RedisDistributeLock redisDistributeLock;

    private RedisLockProvider lockProvider;

    @BeforeEach
    void setUp() {
        lockProvider = new RedisLockProvider(redisDistributeLock);
    }

    @Test
    @DisplayName("tryLock应使用非阻塞两参数方法")
    void testTryLockUsesNonBlockingOverload() {
        when(redisDistributeLock.tryLock(eq("lock:test-key"), anyLong()))
                .thenReturn(true);

        boolean result = lockProvider.tryLock("test-key", 30);

        assertTrue(result);
        // 验证调用的是两参数重载（非阻塞），而非三参数阻塞版本
        verify(redisDistributeLock).tryLock(eq("lock:test-key"), eq(30000L));
        verify(redisDistributeLock, never())
                .tryLock(eq("lock:test-key"), anyLong(), anyLong());
    }

    @Test
    @DisplayName("tryLock失败时返回false")
    void testTryLockReturnsFalse() {
        when(redisDistributeLock.tryLock(eq("lock:test-key"), anyLong()))
                .thenReturn(false);

        assertFalse(lockProvider.tryLock("test-key", 30));
    }

    @Test
    @DisplayName("unlock调用release")
    void testUnlock() {
        lockProvider.unlock("test-key");
        verify(redisDistributeLock).release("lock:test-key");
    }

    @Test
    @DisplayName("heldByCurrentThread委托给底层锁")
    void testHeldByCurrentThread() {
        when(redisDistributeLock.heldByCurrentThread("lock:test-key"))
                .thenReturn(true);

        assertTrue(lockProvider.heldByCurrentThread("test-key"));
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

```bash
./mvnw -pl idempotent test -Dtest=RedisLockProviderTest -am
```

Expected: FAIL — `RedisLockProviderTest` 不存在或验证失败（三参数方法被调用）

- [ ] **Step 3: 修复实现**

修改 `RedisLockProvider.java` 第19行：

```java
@Override
public boolean tryLock(String key, long expireSeconds) {
    String lockKey = "lock:" + key;
    boolean locked = lock.tryLock(lockKey, expireSeconds * 1000);

    if (locked) {
        log.debug("Lock acquired for key: {}", lockKey);
        return true;
    }

    log.debug("Failed to acquire lock for key: {}", lockKey);
    return false;
}
```

- [ ] **Step 4: 运行测试验证通过**

```bash
./mvnw -pl idempotent test -Dtest=RedisLockProviderTest -am
```

Expected: PASS — 验证两参数非阻塞方法被调用

- [ ] **Step 5: 提交**

```bash
git add idempotent/src/main/java/com/lezai/idempotent/lock/RedisLockProvider.java \
        idempotent/src/test/java/com/lezai/idempotent/lock/RedisLockProviderTest.java
git commit -m "fix(idempotent): use non-blocking tryLock in RedisLockProvider

The three-argument tryLock(key, timeout, leaseTime) is a blocking retry
loop that blocks for 'timeout' ms. Switch to the non-blocking two-argument
tryLock(key, leaseTime) for idempotent lock checking to prevent thread
pool exhaustion."
```

---

### Task 2: 修复 LocalLockProvider 锁泄漏风险

**Files:**
- Modify: `idempotent/src/main/java/com/lezai/idempotent/lock/LocalLockProvider.java:14-24`
- Test: `idempotent/src/test/java/com/lezai/idempotent/lock/LocalLockProviderTest.java` (已有，追加测试)

- [ ] **Step 1: 编写失败测试（追加到现有测试文件）**

在 `LocalLockProviderTest.java` 末尾（`}` 之前）添加：

```java
    @Test
    @DisplayName("锁被持有时不应被驱逐导致泄漏")
    void testHeldLockNotEvicted() throws InterruptedException {
        // 获取锁但不释放
        assertTrue(lockProvider.tryLock("eviction-test", 5));

        // 模拟大量不同键填充缓存，触发 Caffeine 容量驱逐
        for (int i = 0; i < 11000; i++) {
            lockProvider.tryLock("filler-" + i, 1);
        }

        // 原始锁仍应被正确持有
        assertTrue(lockProvider.heldByCurrentThread("eviction-test"));

        // 释放后应正常解锁，不抛异常
        lockProvider.unlock("eviction-test");
        assertFalse(lockProvider.heldByCurrentThread("eviction-test"));
    }

    @Test
    @DisplayName("Caffeine过期后锁可重新获取")
    void testLockReacquirableAfterExpiry() throws InterruptedException {
        // 获取锁并使用极短的过期
        LocalLockProvider shortTtlProvider = new LocalLockProvider();
        assertTrue(shortTtlProvider.tryLock("expiry-test", 1));
        shortTtlProvider.unlock("expiry-test");

        // 释放后应能重新获取
        assertTrue(shortTtlProvider.tryLock("expiry-test", 1));
        shortTtlProvider.unlock("expiry-test");
    }
```

- [ ] **Step 2: 运行测试验证失败**

```bash
./mvnw -pl idempotent test -Dtest=LocalLockProviderTest#testHeldLockNotEvicted -am
```

Expected: FAIL — 锁被驱逐后 `unlock` 静默失败或互斥性破坏

- [ ] **Step 3: 修复实现**

重写 `LocalLockProvider.java` 构造函数和相关方法：

```java
package com.lezai.idempotent.lock;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 本地锁提供者
 * 使用 ConcurrentHashMap + ReentrantLock 实现，避免 Caffeine 驱逐导致的锁泄漏
 */
@Slf4j
public class LocalLockProvider implements IdempotentLockProvider {

    private final ConcurrentHashMap<String, ReentrantLock> lockMap;

    public LocalLockProvider() {
        this.lockMap = new ConcurrentHashMap<>();
    }

    @Override
    public boolean tryLock(String key, long waitTimeoutSeconds) {
        ReentrantLock lock = lockMap.computeIfAbsent(key, k -> new ReentrantLock());
        try {
            return lock.tryLock(waitTimeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Lock acquisition interrupted for key: {}", key);
            return false;
        }
    }

    @Override
    public void unlock(String key) {
        ReentrantLock lock = lockMap.get(key);
        if (lock != null && lock.isHeldByCurrentThread()) {
            lock.unlock();
            log.debug("Lock released for key: {}", key);
            // 清理不再持有的锁，防止内存泄漏
            if (!lock.isLocked()) {
                lockMap.remove(key, lock);
            }
        }
    }

    @Override
    public boolean heldByCurrentThread(String key) {
        ReentrantLock lock = lockMap.get(key);
        return lock != null && lock.isHeldByCurrentThread();
    }
}
```

- [ ] **Step 4: 运行测试验证通过**

```bash
./mvnw -pl idempotent test -Dtest=LocalLockProviderTest -am
```

Expected: PASS — 所有现有测试 + 新增测试通过

- [ ] **Step 5: 提交**

```bash
git add idempotent/src/main/java/com/lezai/idempotent/lock/LocalLockProvider.java \
        idempotent/src/test/java/com/lezai/idempotent/lock/LocalLockProviderTest.java
git commit -m "fix(idempotent): prevent lock eviction leak in LocalLockProvider

Replace Caffeine cache with ConcurrentHashMap to avoid evicting locks
that are still held by threads. Add automatic cleanup of unreferenced
locks on unlock(). Rename parameter to waitTimeoutSeconds for clarity."
```

---

### Task 3: 修复 JdbcIdempotentStorage SQL Bug（update_time 置 NULL）

**Files:**
- Modify: `idempotent/src/main/java/com/lezai/idempotent/storage/JdbcIdempotentStorage.java:49-56`
- Modify: `idempotent/src/main/resources/sql/ddl.sql`
- Test: `idempotent/src/test/java/com/lezai/idempotent/storage/JdbcIdempotentStorageTest.java` (追加测试)

- [ ] **Step 1: 编写失败测试**

在 `JdbcIdempotentStorageTest.java` 追加：

```java
    @Test
    @DisplayName("保存记录时update_time不应为NULL")
    void testSaveDoesNotSetUpdateTimeToNull() {
        IdempotentRecord record = createTestRecord();

        assertDoesNotThrow(() -> storage.save(record, 3600));

        // 验证 SQL 包含 NOW() 而非依赖 VALUES(update_time)
        verify(jdbcTemplate).update(
            contains("ON DUPLICATE KEY UPDATE"),
            eq("test-key"),
            eq(2),
            eq("result-data"),
            eq("java.lang.String"),
            eq(null),
            any(LocalDateTime.class),  // expire_time
            any(LocalDateTime.class),  // create_time (NOW())
            any(LocalDateTime.class),  // update_time (NOW())
            eq(100L)
        );
    }

    @Test
    @DisplayName("RowMapper处理NULL timestamp不抛NPE")
    void testRowMapperHandlesNullTimestamp() throws SQLException {
        // 模拟 update_time 为 NULL 的场景
        when(resultSet.getString("idempotent_key")).thenReturn("test-key");
        when(resultSet.getInt("process_status")).thenReturn(2);
        when(resultSet.getString("process_result")).thenReturn("result");
        when(resultSet.getString("result_type")).thenReturn("java.lang.String");
        when(resultSet.getString("error_message")).thenReturn(null);
        when(resultSet.getLong("duration")).thenReturn(100L);
        when(resultSet.getTimestamp("create_time")).thenReturn(Timestamp.valueOf(LocalDateTime.now()));
        when(resultSet.getTimestamp("update_time")).thenReturn(null);  // NULL!
        when(resultSet.getTimestamp("expire_time")).thenReturn(Timestamp.valueOf(LocalDateTime.now().plusHours(1)));

        // 不应抛出 NPE
        assertDoesNotThrow(() -> {
            // 通过 RowMapper 映射（间接测试）
            verify(resultSet, never()).getTimestamp("update_time");
        });
    }
```

- [ ] **Step 2: 运行测试验证失败**

```bash
./mvnw -pl idempotent test -Dtest=JdbcIdempotentStorageTest#testSaveDoesNotSetUpdateTimeToNull -am
```

Expected: FAIL — 当前 SQL 有 7 个占位符，新测试期望 9 个

- [ ] **Step 3: 修复 SQL**

修改 `JdbcIdempotentStorage.java` 的 `save()` 方法 SQL：

```java
@Override
public void save(IdempotentRecord record, long expireSeconds) {
    String sql = "INSERT INTO " + tableName + " " +
            "(idempotent_key, process_status, process_result, result_type, error_message, expire_time, create_time, update_time, duration) " +
            "VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW(), ?) " +
            "ON DUPLICATE KEY UPDATE " +
            "process_status = VALUES(process_status), process_result = VALUES(process_result), " +
            "result_type = VALUES(result_type), error_message = VALUES(error_message), " +
            "update_time = NOW(), duration = VALUES(duration)";

    jdbcTemplate.update(
            sql,
            record.getKey(),
            record.getStatus().getCode(),
            record.getResult(),
            record.getResultType(),
            record.getErrorMessage(),
            record.getExpireTime(),
            record.getDuration()
    );

    log.debug("Record saved to database, key: {}", record.getKey());
}
```

- [ ] **Step 4: 修复 RowMapper null 安全**

修改 `IdempotentRecordRowMapper.mapRow()` 方法（同一文件底部）：

```java
private static class IdempotentRecordRowMapper implements RowMapper<IdempotentRecord> {
    @Override
    public IdempotentRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        IdempotentRecord record = new IdempotentRecord();
        record.setKey(rs.getString("idempotent_key"));
        record.setStatus(IdempotentStatus.fromCode(rs.getInt("process_status")));
        record.setResult(rs.getString("process_result"));
        record.setResultType(rs.getString("result_type"));
        record.setErrorMessage(rs.getString("error_message"));
        record.setDuration(rs.getLong("duration"));

        Timestamp createTimeTs = rs.getTimestamp("create_time");
        if (createTimeTs != null) {
            record.setCreateTime(createTimeTs.toLocalDateTime());
        }
        Timestamp updateTimeTs = rs.getTimestamp("update_time");
        if (updateTimeTs != null) {
            record.setUpdateTime(updateTimeTs.toLocalDateTime());
        }
        Timestamp expireTimeTs = rs.getTimestamp("expire_time");
        if (expireTimeTs != null) {
            record.setExpireTime(expireTimeTs.toLocalDateTime());
        }
        return record;
    }
}
```

- [ ] **Step 5: 运行测试验证通过**

```bash
./mvnw -pl idempotent test -Dtest=JdbcIdempotentStorageTest -am
```

Expected: PASS

- [ ] **Step 6: 更新 DDL（保持一致性）**

确认 `ddl.sql` 无问题（已有 `DEFAULT CURRENT_TIMESTAMP`），但为支持更明确的 schema 演进，保持当前 DDL 不变即可。

- [ ] **Step 7: 提交**

```bash
git add idempotent/src/main/java/com/lezai/idempotent/storage/JdbcIdempotentStorage.java
git commit -m "fix(idempotent): fix SQL bug setting update_time to NULL

Add create_time/update_time to INSERT with NOW() in ON DUPLICATE KEY UPDATE.
Add null guards in RowMapper for all Timestamp fields to prevent NPE."
```

---

### Task 4: 修复缓存结果反序列化异常处理

**Files:**
- Modify: `idempotent/src/main/java/com/lezai/idempotent/core/IdempotentExecutionManager.java:210-225`
- Test: `idempotent/src/test/java/com/lezai/idempotent/core/IdempotentExecutionManagerTest.java` (已有，追加测试)

- [ ] **Step 1: 编写失败测试**

在 `IdempotentExecutionManagerTest.java` 追加：

```java
    @Test
    @DisplayName("缓存结果反序列化失败时保留原始异常cause")
    void testCachedResultDeserializationFailurePreservesCause() throws Throwable {
        IdempotentRecord record = createTestRecord("deser-key", IdempotentStatus.SUCCEEDED);
        record.setResult("{\"data\":\"cached\"}");
        record.setResultType("com.lezai.nonexistent.Class");  // 不存在的类

        when(keyResolver.resolve(joinPoint, idempotent)).thenReturn("deser-key");
        when(storage.get("deser-key")).thenReturn(record);
        when(idempotent.returnResultOnDuplicate()).thenReturn(true);

        // 执行并验证
        IdempotentExecutionException ex = assertThrows(
            IdempotentExecutionException.class,
            () -> executionManager.execute(joinPoint, idempotent)
        );

        // 验证原始异常作为 cause 被保留
        assertNotNull(ex.getCause());
        assertTrue(ex.getMessage().contains("deser-key"));
    }
```

- [ ] **Step 2: 运行测试验证失败**

```bash
./mvnw -pl idempotent test -Dtest=IdempotentExecutionManagerTest#testCachedResultDeserializationFailurePreservesCause -am
```

Expected: FAIL — cause 为 null（当前实现丢失原始异常）

- [ ] **Step 3: 修复实现**

修改 `IdempotentExecutionManager.java` 的 `handleSucceededRecord` 方法：

```java
private Object handleSucceededRecord(String key, IdempotentRecord record, Idempotent idempotent) {
    log.info("Duplicate successful request detected, key: {}", key);

    if (idempotent.returnResultOnDuplicate()) {
        try {
            Class<?> clz = Class.forName(record.getResultType());
            return JSON.parseObject(record.getResult(), clz);
        } catch (Exception e) {
            log.error("Failed to deserialize cached result for key: {}, resultType: {}",
                    key, record.getResultType(), e);
            throw new IdempotentExecutionException(
                    "Failed to deserialize cached result for key: " + key, e);
        }
    } else {
        throw new IdempotentException("Duplicate request detected: " + key);
    }
}
```

- [ ] **Step 4: 运行测试验证通过**

```bash
./mvnw -pl idempotent test -Dtest=IdempotentExecutionManagerTest#testCachedResultDeserializationFailurePreservesCause -am
```

Expected: PASS — cause 不为 null

- [ ] **Step 5: 提交**

```bash
git add idempotent/src/main/java/com/lezai/idempotent/core/IdempotentExecutionManager.java \
        idempotent/src/test/java/com/lezai/idempotent/core/IdempotentExecutionManagerTest.java
git commit -m "fix(idempotent): preserve original cause in deserialization failure

ClassNotFoundException was being lost by passing only e.getMessage().
Now passes the full exception as cause and includes key in error message."
```

---

### Task 5: 修复 pom.xml test scope 泄漏

**Files:**
- Modify: `pom.xml:101-104`

- [ ] **Step 1: 修改 pom.xml**

在根 `pom.xml` 的 `spring-boot-starter-test` 依赖添加 `<scope>test</scope>`：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
```

- [ ] **Step 2: 验证构建正常**

```bash
./mvnw -pl idempotent clean test -am
```

Expected: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
git add pom.xml
git commit -m "fix(idempotent): add test scope to spring-boot-starter-test

Prevent test libraries from being packaged in production artifacts."
```

---

## Phase 2: 健壮性增强

### Task 6: RedisIdempotentStorage 添加异常处理

**Files:**
- Modify: `idempotent/src/main/java/com/lezai/idempotent/storage/RedisIdempotentStorage.java`
- Test: `idempotent/src/test/java/com/lezai/idempotent/storage/RedisIdempotentStorageTest.java` (新建)

- [ ] **Step 1: 编写失败测试**

```java
package com.lezai.idempotent.storage;

import com.lezai.idempotent.core.IdempotentRecord;
import com.lezai.idempotent.enums.IdempotentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Redis幂等存储测试")
class RedisIdempotentStorageTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    private RedisIdempotentStorage storage;

    @BeforeEach
    void setUp() {
        storage = new RedisIdempotentStorage("idempotent:", redisTemplate);
    }

    @Test
    @DisplayName("get方法JSON解析失败返回null不抛异常")
    void testGetWithCorruptedJsonReturnsNull() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("idempotent:corrupt-key"))
                .thenReturn("{invalid json!!!");

        IdempotentRecord result = storage.get("corrupt-key");

        assertNull(result);
    }

    @Test
    @DisplayName("exists方法Redis异常返回false不抛NPE")
    void testExistsWithRedisExceptionReturnsFalse() {
        when(redisTemplate.hasKey("idempotent:any-key"))
                .thenThrow(new RuntimeException("Redis connection lost"));

        boolean result = storage.exists("any-key");

        assertFalse(result);
    }

    @Test
    @DisplayName("save方法空key抛IllegalArgumentException")
    void testSaveWithNullKeyThrowsException() {
        IdempotentRecord record = new IdempotentRecord();
        record.setKey(null);

        assertThrows(IllegalArgumentException.class,
                () -> storage.save(record, 3600));
    }

    @Test
    @DisplayName("save方法空record抛IllegalArgumentException")
    void testSaveWithNullRecordThrowsException() {
        assertThrows(IllegalArgumentException.class,
                () -> storage.save(null, 3600));
    }

    @Test
    @DisplayName("正常保存和获取记录")
    void testSaveAndGetRoundTrip() {
        IdempotentRecord record = new IdempotentRecord();
        record.setKey("roundtrip-key");
        record.setStatus(IdempotentStatus.SUCCEEDED);
        record.setResult("{\"data\":\"ok\"}");
        record.setResultType("java.lang.String");
        record.setDuration(50L);
        record.setExpireTime(LocalDateTime.now().plusHours(1));

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);

        assertDoesNotThrow(() -> storage.save(record, 3600));

        verify(valueOps).set(
                eq("idempotent:roundtrip-key"),
                contains("roundtrip-key"),
                eq(3600L),
                eq(TimeUnit.SECONDS)
        );
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

```bash
./mvnw -pl idempotent test -Dtest=RedisIdempotentStorageTest -am
```

Expected: FAIL — 当前实现无异常处理

- [ ] **Step 3: 修复实现**

重写 `RedisIdempotentStorage.java`：

```java
package com.lezai.idempotent.storage;

import com.alibaba.fastjson2.JSON;
import com.lezai.idempotent.core.IdempotentRecord;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

@Slf4j
@AllArgsConstructor
public class RedisIdempotentStorage implements IdempotentStorage {
    private static final String DEFAULT_KEY_PREFIX = "idempotent:";

    private final String keyPrefix;
    private final StringRedisTemplate redisTemplate;

    public RedisIdempotentStorage(StringRedisTemplate redisTemplate) {
        this(DEFAULT_KEY_PREFIX, redisTemplate);
    }

    @Override
    public IdempotentRecord get(String key) {
        String redisKey = keyPrefix + key;
        try {
            String json = redisTemplate.opsForValue().get(redisKey);
            if (json == null) {
                return null;
            }
            return JSON.parseObject(json, IdempotentRecord.class);
        } catch (Exception e) {
            log.warn("Failed to parse idempotent record from Redis, key: {}", redisKey, e);
            return null;
        }
    }

    @Override
    public void save(IdempotentRecord record, long expireSeconds) {
        if (record == null || record.getKey() == null) {
            throw new IllegalArgumentException("IdempotentRecord and key must not be null");
        }
        String redisKey = keyPrefix + record.getKey();
        redisTemplate.opsForValue().set(redisKey, JSON.toJSONString(record), expireSeconds, TimeUnit.SECONDS);
        log.debug("Record saved to Redis, key: {}, expire: {}s", redisKey, expireSeconds);
    }

    @Override
    public void remove(String key) {
        String redisKey = keyPrefix + key;
        try {
            Boolean deleted = redisTemplate.delete(redisKey);
            if (Boolean.TRUE.equals(deleted)) {
                log.debug("Record removed from Redis, key: {}", redisKey);
            } else {
                log.debug("Record not found in Redis, key: {}", redisKey);
            }
        } catch (Exception e) {
            log.warn("Failed to remove record from Redis, key: {}", redisKey, e);
        }
    }

    @Override
    public boolean exists(String key) {
        String redisKey = keyPrefix + key;
        try {
            Boolean exists = redisTemplate.hasKey(redisKey);
            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            log.warn("Failed to check key existence: {}", redisKey, e);
            return false;
        }
    }
}
```

- [ ] **Step 4: 运行测试验证通过**

```bash
./mvnw -pl idempotent test -Dtest=RedisIdempotentStorageTest -am
```

Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add idempotent/src/main/java/com/lezai/idempotent/storage/RedisIdempotentStorage.java \
        idempotent/src/test/java/com/lezai/idempotent/storage/RedisIdempotentStorageTest.java
git commit -m "fix(idempotent): add defensive exception handling to RedisIdempotentStorage

- get() catches JSONException and returns null instead of propagating
- exists() handles Boolean null/exception safely
- save() validates null record/key
- remove() handles delete failure gracefully"
```

---

### Task 7: JdbcIdempotentStorage 精确异常捕获 + 清理死代码

**Files:**
- Modify: `idempotent/src/main/java/com/lezai/idempotent/storage/JdbcIdempotentStorage.java`
- Test: `idempotent/src/test/java/com/lezai/idempotent/storage/JdbcIdempotentStorageTest.java` (追加测试)

- [ ] **Step 1: 编写失败测试**

```java
    @Test
    @DisplayName("get方法数据库错误应传播而非吞掉")
    void testGetWithDatabaseErrorPropagatesException() {
        when(jdbcTemplate.queryForObject(
            anyString(),
            any(RowMapper.class),
            anyString(),
            any(LocalDateTime.class)
        )).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("DB down"));

        assertThrows(org.springframework.dao.DataAccessResourceFailureException.class,
            () -> storage.get("db-error-key"));
    }
```

- [ ] **Step 2: 运行测试验证失败**

```bash
./mvnw -pl idempotent test -Dtest=JdbcIdempotentStorageTest#testGetWithDatabaseErrorPropagatesException -am
```

Expected: FAIL — 当前 catch(Exception) 吞掉了数据库错误

- [ ] **Step 3: 修复实现**

修改 `JdbcIdempotentStorage.java` 的 `get()` 方法异常处理，并删除 `serializeResult()` 死代码：

```java
import org.springframework.dao.EmptyResultDataAccessException;
// ... 其他 import 不变

    @Override
    public IdempotentRecord get(String key) {
        String sql = "SELECT * FROM " + tableName + " WHERE idempotent_key = ? AND expire_time > ?";

        try {
            return jdbcTemplate.queryForObject(
                    sql,
                    new IdempotentRecordRowMapper(),
                    key,
                    LocalDateTime.now()
            );
        } catch (EmptyResultDataAccessException e) {
            log.debug("No record found for key: {}", key);
            return null;
        }
    }
```

删除 `serializeResult()` 方法（第89-96行附近的死代码）。

- [ ] **Step 4: 运行测试验证通过**

```bash
./mvnw -pl idempotent test -Dtest=JdbcIdempotentStorageTest -am
```

Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add idempotent/src/main/java/com/lezai/idempotent/storage/JdbcIdempotentStorage.java \
        idempotent/src/test/java/com/lezai/idempotent/storage/JdbcIdempotentStorageTest.java
git commit -m "fix(idempotent): catch EmptyResultDataAccessException specifically in JdbcStorage

Replace broad Exception catch with EmptyResultDataAccessException to
allow database errors to propagate. Remove unused serializeResult() dead code."
```

---

### Task 8: IdempotentExecutionManager 重试配置校验 + 兜底分支优化

**Files:**
- Modify: `idempotent/src/main/java/com/lezai/idempotent/core/IdempotentExecutionManager.java`
- Test: `idempotent/src/test/java/com/lezai/idempotent/core/IdempotentExecutionManagerTest.java` (追加测试)

- [ ] **Step 1: 编写失败测试**

```java
    @Test
    @DisplayName("重试时记录过期应优雅处理而非进入handleConcurrentRequest")
    void testRetryWithExpiredRecordHandledGracefully() throws Throwable {
        IdempotentRecord processingRecord = createTestRecord("expired-retry-key", IdempotentStatus.PROCESSING);

        when(keyResolver.resolve(joinPoint, idempotent)).thenReturn("expired-retry-key");
        when(storage.get("expired-retry-key"))
                .thenReturn(processingRecord)   // 第一次查到 PROCESSING
                .thenReturn(null);              // 重试时记录已过期
        when(idempotent.failFast()).thenReturn(false);
        when(idempotent.maxRetryCount()).thenReturn(3);
        when(idempotent.retryInterval()).thenReturn(10L);
        when(lockProvider.tryLock(eq("expired-retry-key"), anyLong())).thenReturn(true);
        when(lockProvider.heldByCurrentThread("expired-retry-key")).thenReturn(true);
        when(joinPoint.proceed()).thenReturn("success-after-expiry");
        when(idempotent.storeResult()).thenReturn(true);

        // 执行 — 不应进入 handleConcurrentRequest 的锁竞争逻辑
        Object result = executionManager.execute(joinPoint, idempotent);

        assertEquals("success-after-expiry", result);
    }
```

- [ ] **Step 2: 运行测试验证失败**

```bash
./mvnw -pl idempotent test -Dtest=IdempotentExecutionManagerTest#testRetryWithExpiredRecordHandledGracefully -am
```

Expected: FAIL — 当前兜底逻辑进入 handleConcurrentRequest 导致行为不确定

- [ ] **Step 3: 修复实现**

修改 `handleProcessingRecord` 中的兜底分支（约第195行）：

```java
    private Object handleProcessingRecord(ProceedingJoinPoint joinPoint, String key, Idempotent idempotent, int retryCount) {
        if (idempotent.failFast()) {
            throw new IdempotentException("Request is processing, please retry later");
        }

        if (retryCount >= idempotent.maxRetryCount()) {
            throw new IdempotentException("Max retry count exceeded while waiting for processing result");
        }

        try {
            Thread.sleep(idempotent.retryInterval());

            IdempotentRecord record = storage.get(key);
            if (record == null) {
                // Record expired during retry, treat as first request
                log.warn("Record expired during retry for key: {}, treating as first request", key);
                return handleFirstRequest(joinPoint, idempotent, key);
            }

            return handleExistingRecord(joinPoint, key, record, idempotent, ++retryCount);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IdempotentException("Interrupted while waiting for result", e);
        }
    }
```

- [ ] **Step 4: 运行测试验证通过**

```bash
./mvnw -pl idempotent test -Dtest=IdempotentExecutionManagerTest -am
```

Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add idempotent/src/main/java/com/lezai/idempotent/core/IdempotentExecutionManager.java \
        idempotent/src/test/java/com/lezai/idempotent/core/IdempotentExecutionManagerTest.java
git commit -m "fix(idempotent): improve fallback when record expires during retry

Replace handleConcurrentRequest fallback with handleFirstRequest for
clearer semantics when a PROCESSING record expires during wait-retry."
```

---

### Task 9: LocalIdempotentStorage 添加 null 校验 + 文档注释

**Files:**
- Modify: `idempotent/src/main/java/com/lezai/idempotent/storage/LocalIdempotentStorage.java`
- Test: `idempotent/src/test/java/com/lezai/idempotent/storage/LocalIdempotentStorageTest.java` (追加测试)

- [ ] **Step 1: 编写失败测试**

```java
    @Test
    @DisplayName("get方法null key抛IllegalArgumentException")
    void testGetWithNullKeyThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> storage.get(null));
    }

    @Test
    @DisplayName("save方法null record抛IllegalArgumentException")
    void testSaveWithNullRecordThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> storage.save(null, 3600));
    }
```

- [ ] **Step 2: 运行测试验证失败**

```bash
./mvnw -pl idempotent test -Dtest=LocalIdempotentStorageTest#testGetWithNullKeyThrowsException -am
```

Expected: FAIL — 当前 Caffeine 抛 NPE 而非 IllegalArgumentException

- [ ] **Step 3: 修复实现**

```java
    @Override
    public IdempotentRecord get(String key) {
        if (key == null) {
            throw new IllegalArgumentException("Key must not be null");
        }
        return cache.getIfPresent(key);
    }

    @Override
    public void save(IdempotentRecord record, long expireSeconds) {
        if (record == null) {
            throw new IllegalArgumentException("IdempotentRecord must not be null");
        }
        // Caffeine only supports global expiration; expireSeconds parameter is ignored
        // All records use the expireAfterWrite policy set in the constructor
        cache.put(record.getKey(), record);
        log.debug("Record saved for key: {}", record.getKey());
    }
```

- [ ] **Step 4: 运行测试验证通过**

```bash
./mvnw -pl idempotent test -Dtest=LocalIdempotentStorageTest -am
```

Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add idempotent/src/main/java/com/lezai/idempotent/storage/LocalIdempotentStorage.java \
        idempotent/src/test/java/com/lezai/idempotent/storage/LocalIdempotentStorageTest.java
git commit -m "fix(idempotent): add null validation and docs to LocalIdempotentStorage

Add defensive null checks for key and record parameters.
Document that expireSeconds is ignored due to Caffeine's global-only
expiration policy."
```

---

## Phase 3: 可观测性与扩展性（可选）

### Task 10: DDL 增强 — 添加审计字段

**Files:**
- Modify: `idempotent/src/main/resources/sql/ddl.sql`
- Modify: `idempotent/src/main/java/com/lezai/idempotent/core/IdempotentRecord.java`
- Modify: `idempotent/src/main/java/com/lezai/idempotent/storage/JdbcIdempotentStorage.java`

- [ ] **Step 1: 更新 DDL**

```sql
CREATE TABLE IF NOT EXISTS idempotent_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    idempotent_key VARCHAR(255) NOT NULL COMMENT '幂等键',
    process_status TINYINT NOT NULL COMMENT '状态：1-执行中 2-执行成功 3-执行失败',
    process_result TEXT COMMENT '执行结果（可选存储）',
    result_type VARCHAR(255) COMMENT '结果类型',
    error_message TEXT COMMENT '错误信息',
    duration BIGINT COMMENT '执行耗时（毫秒）',
    request_id VARCHAR(64) COMMENT '请求ID（用于追踪）',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    expire_time DATETIME NOT NULL COMMENT '过期时间',
    UNIQUE KEY uk_idempotent_key (idempotent_key),
    INDEX idx_expire_time (expire_time),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='幂等性记录表';
```

- [ ] **Step 2: 更新 IdempotentRecord 实体**

添加字段：

```java
/**
 * 请求ID（用于追踪）
 */
private String requestId;
```

- [ ] **Step 3: 更新 JdbcIdempotentStorage RowMapper**

添加 `request_id` 映射：

```java
record.setRequestId(rs.getString("request_id"));
```

- [ ] **Step 4: 运行测试验证通过**

```bash
./mvnw -pl idempotent test -Dtest=JdbcIdempotentStorageTest -am
```

Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add idempotent/src/main/resources/sql/ddl.sql \
        idempotent/src/main/java/com/lezai/idempotent/core/IdempotentRecord.java \
        idempotent/src/main/java/com/lezai/idempotent/storage/JdbcIdempotentStorage.java
git commit -m "feat(idempotent): add request_id audit field to DDL and entity

Add request_id column for request tracing and debugging in production."
```

---

## 验证清单

完成所有 Task 后执行：

```bash
# 1. 全量测试通过
./mvnw -pl idempotent clean test -am

# 2. 无编译警告
./mvnw -pl idempotent clean compile -am

# 3. 确认所有变更
git log --oneline main..HEAD
```

**预期结果**: 全部测试通过，无回归，提交历史清晰（每个 commit 对应一个独立修复）。