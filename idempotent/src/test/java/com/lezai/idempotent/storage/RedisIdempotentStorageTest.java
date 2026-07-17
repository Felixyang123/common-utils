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

        assertDoesNotThrow(() -> storage.save(record, 3600));

        verify(valueOps).set(
                eq("idempotent:roundtrip-key"),
                contains("roundtrip-key"),
                eq(3600L),
                eq(TimeUnit.SECONDS)
        );
    }

    @Test
    @DisplayName("remove方法删除成功返回true日志")
    void testRemoveSuccess() {
        when(redisTemplate.delete("idempotent:remove-key")).thenReturn(true);

        assertDoesNotThrow(() -> storage.remove("remove-key"));

        verify(redisTemplate).delete("idempotent:remove-key");
    }

    @Test
    @DisplayName("exists方法正常返回true")
    void testExistsReturnsTrue() {
        when(redisTemplate.hasKey("idempotent:exists-key")).thenReturn(true);

        assertTrue(storage.exists("exists-key"));
    }
}
