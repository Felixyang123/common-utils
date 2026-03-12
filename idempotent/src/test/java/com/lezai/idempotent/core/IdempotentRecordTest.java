package com.lezai.idempotent.core;

import com.lezai.idempotent.enums.IdempotentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IdempotentRecord 实体测试
 */
@DisplayName("幂等记录实体测试")
class IdempotentRecordTest {

    @Test
    @DisplayName("创建完整记录")
    void testCreateFullRecord() {
        IdempotentRecord record = new IdempotentRecord();
        LocalDateTime now = LocalDateTime.now();

        record.setKey("test-key-123");
        record.setStatus(IdempotentStatus.SUCCEEDED);
        record.setResult("{\"data\":\"success\"}");
        record.setResultType("java.util.Map");
        record.setErrorMessage(null);
        record.setDuration(150L);
        record.setCreateTime(now);
        record.setUpdateTime(now);
        record.setExpireTime(now.plusHours(1));

        assertEquals("test-key-123", record.getKey());
        assertEquals(IdempotentStatus.SUCCEEDED, record.getStatus());
        assertEquals("{\"data\":\"success\"}", record.getResult());
        assertEquals("java.util.Map", record.getResultType());
        assertNull(record.getErrorMessage());
        assertEquals(150L, record.getDuration());
        assertNotNull(record.getCreateTime());
        assertNotNull(record.getUpdateTime());
        assertNotNull(record.getExpireTime());
    }

    @Test
    @DisplayName("创建处理中记录")
    void testCreateProcessingRecord() {
        IdempotentRecord record = new IdempotentRecord();
        record.setKey("processing-key");
        record.setStatus(IdempotentStatus.PROCESSING);

        assertEquals("processing-key", record.getKey());
        assertEquals(IdempotentStatus.PROCESSING, record.getStatus());
        assertNull(record.getResult());
    }

    @Test
    @DisplayName("创建失败记录")
    void testCreateFailedRecord() {
        IdempotentRecord record = new IdempotentRecord();
        record.setKey("failed-key");
        record.setStatus(IdempotentStatus.FAILED);
        record.setErrorMessage("Business exception occurred");
        record.setDuration(50L);

        assertEquals("failed-key", record.getKey());
        assertEquals(IdempotentStatus.FAILED, record.getStatus());
        assertEquals("Business exception occurred", record.getErrorMessage());
        assertEquals(50L, record.getDuration());
    }

    @Test
    @DisplayName("空值处理")
    void testNullValues() {
        IdempotentRecord record = new IdempotentRecord();

        assertNull(record.getKey());
        assertNull(record.getStatus());
        assertNull(record.getResult());
        assertNull(record.getResultType());
        assertNull(record.getErrorMessage());
        assertNull(record.getCreateTime());
        assertNull(record.getUpdateTime());
        assertNull(record.getExpireTime());
        assertNull(record.getDuration());
    }

    @Test
    @DisplayName("更新记录状态")
    void testUpdateRecordStatus() {
        IdempotentRecord record = new IdempotentRecord();
        record.setKey("update-key");
        record.setStatus(IdempotentStatus.PROCESSING);
        record.setCreateTime(LocalDateTime.now());

        // 更新状态为成功
        record.setStatus(IdempotentStatus.SUCCEEDED);
        record.setResult("success-result");
        record.setResultType("java.lang.String");
        record.setDuration(100L);
        record.setUpdateTime(LocalDateTime.now());

        assertEquals(IdempotentStatus.SUCCEEDED, record.getStatus());
        assertEquals("success-result", record.getResult());
        assertEquals("java.lang.String", record.getResultType());
        assertEquals(100L, record.getDuration());
    }
}
