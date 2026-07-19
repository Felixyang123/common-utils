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

/**
 * Redis 幂等存储测试 (ADR-0002 接口契约验证):
 * get/remove/exists 在 Redis 异常/反序列化异常时上抛 —— 不允许把异常伪装成 null/false
 */
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
    @DisplayName("get 方法 JSON 解析失败时不吞异常 —— 装饰损坏应当上抛")
    void testGetWithCorruptedJsonThrowsException() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("idempotent:corrupt-key"))
                .thenReturn("{invalid json!!!");

        // 不再伪装成 null,直接上抛 (fast-fail 触发调用方 503+Retry-After)
        assertThrows(Exception.class, () -> storage.get("corrupt-key"));
    }

    @Test
    @DisplayName("get 方法 Redis 连接异常上抛而不是返回 null")
    void testGetWithRedisConnectionExceptionThrowsException() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("idempotent:any-key"))
                .thenThrow(new RuntimeException("Redis connection lost"));

        assertThrows(RuntimeException.class, () -> storage.get("any-key"));
    }

    @Test
    @DisplayName("exists 方法 Redis 异常上抛不返回 false")
    void testExistsWithRedisExceptionThrowsException() {
        when(redisTemplate.hasKey("idempotent:any-key"))
                .thenThrow(new RuntimeException("Redis connection lost"));

        // 不存在"无记录"语义 —— 存疑时上抛异常而不是装死
        assertThrows(RuntimeException.class, () -> storage.exists("any-key"));
    }

    @Test
    @DisplayName("get 未命中返回 null(这是唯一合法的 '无记录' 语义)")
    void testGetWithMissingKeyReturnsNull() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("idempotent:missing-key")).thenReturn(null);

        assertNull(storage.get("missing-key"));
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

        assertDoesNotThrow(() -> storage.save(record, 3600));

        verify(valueOps).set(
                eq("idempotent:roundtrip-key"),
                contains("roundtrip-key"),
                eq(3600L),
                eq(TimeUnit.SECONDS)
        );
    }

    @Test
    @DisplayName("remove 方法不吞异常 —— Redis 异常上抛")
    void testRemoveWithRedisExceptionThrowsException() {
        when(redisTemplate.delete("idempotent:remove-key"))
                .thenThrow(new RuntimeException("Redis connection lost"));

        assertThrows(RuntimeException.class, () -> storage.remove("remove-key"));
    }

    @Test
    @DisplayName("remove 方法正常删除")
    void testRemoveSuccess() {
        when(redisTemplate.delete("idempotent:remove-key")).thenReturn(true);

        assertDoesNotThrow(() -> storage.remove("remove-key"));

        verify(redisTemplate).delete("idempotent:remove-key");
    }

    @Test
    @DisplayName("exists 方法命中返回 true")
    void testExistsReturnsTrue() {
        when(redisTemplate.hasKey("idempotent:exists-key")).thenReturn(true);

        assertTrue(storage.exists("exists-key"));
    }

    @Test
    @DisplayName("exists 方法未命中返回 false")
    void testExistsReturnsFalse() {
        when(redisTemplate.hasKey("idempotent:missing-key")).thenReturn(false);

        assertFalse(storage.exists("missing-key"));
    }
}
