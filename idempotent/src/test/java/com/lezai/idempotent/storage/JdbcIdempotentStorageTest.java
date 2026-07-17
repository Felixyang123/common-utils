package com.lezai.idempotent.storage;

import com.lezai.idempotent.core.IdempotentRecord;
import com.lezai.idempotent.enums.IdempotentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.*;

/**
 * JdbcIdempotentStorage 单元测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("JDBC幂等存储测试")
class JdbcIdempotentStorageTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private ResultSet resultSet;

    private JdbcIdempotentStorage storage;

    @BeforeEach
    void setUp() {
        storage = new JdbcIdempotentStorage(jdbcTemplate);
    }

    @Test
    @DisplayName("自定义表名构造")
    void testCustomTableName() {
        JdbcIdempotentStorage customStorage = new JdbcIdempotentStorage("custom_table", jdbcTemplate);
        assertNotNull(customStorage);
    }

    @Test
    @DisplayName("获取存在的记录")
    void testGetExistingRecord() throws SQLException {
        IdempotentRecord expectedRecord = createTestRecord();
        
        when(jdbcTemplate.queryForObject(
            anyString(), 
            any(RowMapper.class), 
            eq("test-key"), 
            any(LocalDateTime.class)
        )).thenReturn(expectedRecord);

        IdempotentRecord result = storage.get("test-key");

        assertNotNull(result);
        assertEquals("test-key", result.getKey());
        assertEquals(IdempotentStatus.SUCCEEDED, result.getStatus());
    }

    @Test
    @DisplayName("获取不存在的记录返回null")
    void testGetNonExistentRecord() {
        when(jdbcTemplate.queryForObject(
            anyString(),
            any(RowMapper.class),
            anyString(),
            any(LocalDateTime.class)
        )).thenThrow(new org.springframework.dao.EmptyResultDataAccessException(1));

        IdempotentRecord result = storage.get("non-existent-key");

        assertNull(result);
    }

    @Test
    @DisplayName("保存记录-插入新记录")
    void testSaveNewRecord() {
        IdempotentRecord record = createTestRecord();

        lenient().when(jdbcTemplate.update(
            anyString(),
            any(), any(), any(), any(), any(), any(), any(), any()
        )).thenReturn(1);

        assertDoesNotThrow(() -> storage.save(record, 3600));

        verify(jdbcTemplate).update(
            contains("INSERT INTO"),
            eq("test-key"),
            eq(2),
            eq("result-data"),
            eq("java.lang.String"),
            eq(null),
            any(LocalDateTime.class),
            eq(100L)
        );
    }

    @Test
    @DisplayName("删除记录")
    void testRemoveRecord() {
        when(jdbcTemplate.update(anyString(), eq("remove-key"))).thenReturn(1);

        assertDoesNotThrow(() -> storage.remove("remove-key"));
        
        verify(jdbcTemplate).update(
            contains("DELETE FROM"),
            eq("remove-key")
        );
    }

    @Test
    @DisplayName("检查存在的记录")
    void testExistsTrue() {
        when(jdbcTemplate.queryForObject(
            anyString(), 
            eq(Integer.class), 
            eq("exists-key"), 
            any(LocalDateTime.class)
        )).thenReturn(1);

        boolean exists = storage.exists("exists-key");

        assertTrue(exists);
    }

    @Test
    @DisplayName("检查不存在的记录")
    void testExistsFalse() {
        when(jdbcTemplate.queryForObject(
            anyString(), 
            eq(Integer.class), 
            eq("not-exists-key"), 
            any(LocalDateTime.class)
        )).thenReturn(0);

        boolean exists = storage.exists("not-exists-key");

        assertFalse(exists);
    }

    @Test
    @DisplayName("检查记录-count为null")
    void testExistsCountNull() {
        when(jdbcTemplate.queryForObject(
            anyString(), 
            eq(Integer.class), 
            anyString(), 
            any(LocalDateTime.class)
        )).thenReturn(null);

        boolean exists = storage.exists("null-count-key");

        assertFalse(exists);
    }

    @Test
    @DisplayName("JdbcIdempotentStorage基本功能测试")
    void testJdbcIdempotentStorageBasic() {
        // 测试不同表名的构造函数
        JdbcIdempotentStorage customStorage = new JdbcIdempotentStorage("custom_table", jdbcTemplate);
        assertNotNull(customStorage);
        
        // 测试默认表名的构造函数
        JdbcIdempotentStorage defaultStorage = new JdbcIdempotentStorage(jdbcTemplate);
        assertNotNull(defaultStorage);
    }

    @Test
    @DisplayName("保存失败记录")
    void testSaveFailedRecord() {
        IdempotentRecord record = createTestRecord();
        record.setStatus(IdempotentStatus.FAILED);
        record.setErrorMessage("Business error occurred");
        record.setResult(null);
        record.setResultType(null);

        lenient().when(jdbcTemplate.update(
            anyString(),
            any(), any(), any(), any(), any(), any(), any(), any()
        )).thenReturn(1);

        assertDoesNotThrow(() -> storage.save(record, 3600));
    }

    @Test
    @DisplayName("保存记录时update_time使用NOW()而非VALUES(NULL)")
    void testSaveDoesNotSetUpdateTimeToNull() {
        IdempotentRecord record = createTestRecord();

        lenient().when(jdbcTemplate.update(
            anyString(),
            any(), any(), any(), any(), any(), any(), any(), any()
        )).thenReturn(1);

        assertDoesNotThrow(() -> storage.save(record, 3600));

        // 验证 SQL 包含 NOW() 而非依赖 VALUES(update_time)
        verify(jdbcTemplate).update(
            contains("update_time = NOW()"),
            eq("test-key"),
            eq(2),
            eq("result-data"),
            eq("java.lang.String"),
            eq(null),
            any(LocalDateTime.class),
            eq(100L)
        );
    }

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

    // ==================== 辅助方法 ====================

    private IdempotentRecord createTestRecord() {
        IdempotentRecord record = new IdempotentRecord();
        record.setKey("test-key");
        record.setStatus(IdempotentStatus.SUCCEEDED);
        record.setResult("result-data");
        record.setResultType("java.lang.String");
        record.setErrorMessage(null);
        record.setDuration(100L);
        record.setCreateTime(LocalDateTime.now());
        record.setUpdateTime(LocalDateTime.now());
        record.setExpireTime(LocalDateTime.now().plusHours(1));
        return record;
    }
}
