package com.lezai.idempotent.storage;

import com.lezai.idempotent.core.IdempotentRecord;
import com.lezai.idempotent.enums.IdempotentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LocalIdempotentStorage 单元测试
 */
@DisplayName("本地幂等存储测试")
class LocalIdempotentStorageTest {

    private LocalIdempotentStorage storage;

    @BeforeEach
    void setUp() {
        storage = new LocalIdempotentStorage(1000, 3600);
    }

    @Test
    @DisplayName("保存和获取记录")
    void testSaveAndGet() {
        IdempotentRecord record = createTestRecord("test-key", IdempotentStatus.SUCCEEDED);
        
        storage.save(record, 3600);
        
        IdempotentRecord retrieved = storage.get("test-key");
        assertNotNull(retrieved);
        assertEquals("test-key", retrieved.getKey());
        assertEquals(IdempotentStatus.SUCCEEDED, retrieved.getStatus());
    }

    @Test
    @DisplayName("获取不存在的记录返回null")
    void testGetNonExistent() {
        IdempotentRecord record = storage.get("non-existent-key");
        assertNull(record);
    }

    @Test
    @DisplayName("检查记录是否存在")
    void testExists() {
        IdempotentRecord record = createTestRecord("exists-key", IdempotentStatus.PROCESSING);
        storage.save(record, 3600);
        
        assertTrue(storage.exists("exists-key"));
        assertFalse(storage.exists("not-exists-key"));
    }

    @Test
    @DisplayName("删除记录")
    void testRemove() {
        IdempotentRecord record = createTestRecord("remove-key", IdempotentStatus.SUCCEEDED);
        storage.save(record, 3600);
        
        assertTrue(storage.exists("remove-key"));
        
        storage.remove("remove-key");
        
        assertFalse(storage.exists("remove-key"));
        assertNull(storage.get("remove-key"));
    }

    @Test
    @DisplayName("更新已存在的记录")
    void testUpdateRecord() {
        IdempotentRecord record = createTestRecord("update-key", IdempotentStatus.PROCESSING);
        storage.save(record, 3600);
        
        // 更新状态
        record.setStatus(IdempotentStatus.SUCCEEDED);
        record.setResult("{\"data\":\"success\"}");
        storage.save(record, 3600);
        
        IdempotentRecord updated = storage.get("update-key");
        assertNotNull(updated);
        assertEquals(IdempotentStatus.SUCCEEDED, updated.getStatus());
        assertEquals("{\"data\":\"success\"}", updated.getResult());
    }

    @Test
    @DisplayName("默认构造函数测试")
    void testDefaultConstructor() {
        LocalIdempotentStorage defaultStorage = new LocalIdempotentStorage();
        
        IdempotentRecord record = createTestRecord("default-key", IdempotentStatus.SUCCEEDED);
        defaultStorage.save(record, 86400);
        
        assertTrue(defaultStorage.exists("default-key"));
    }

    @Test
    @DisplayName("保存多个记录")
    void testSaveMultipleRecords() {
        for (int i = 0; i < 10; i++) {
            IdempotentRecord record = createTestRecord("key-" + i, IdempotentStatus.SUCCEEDED);
            storage.save(record, 3600);
        }
        
        for (int i = 0; i < 10; i++) {
            assertTrue(storage.exists("key-" + i));
        }
    }

    @Test
    @DisplayName("删除不存在的记录不抛异常")
    void testRemoveNonExistent() {
        assertDoesNotThrow(() -> storage.remove("non-existent-key"));
    }

    // 注意:LocalIdempotentStorage 不再做防御性 null 校验 —— 参数由调用方保证(参见 ADR-0002)
    // 删除 testGetWithNullKeyThrowsException / testSaveWithNullRecordThrowsException

    // ==================== 辅助方法 ====================

    private IdempotentRecord createTestRecord(String key, IdempotentStatus status) {
        IdempotentRecord record = new IdempotentRecord();
        record.setKey(key);
        record.setStatus(status);
        record.setCreateTime(LocalDateTime.now());
        record.setUpdateTime(LocalDateTime.now());
        record.setExpireTime(LocalDateTime.now().plusHours(1));
        return record;
    }
}
