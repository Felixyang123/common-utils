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
    @DisplayName("获取不存在的记录返回 null(EmptyResultDataAccessException 转换为 null 语义)")
    void testGetNonExistentRecord() {
        when(jdbcTemplate.queryForObject(
            anyString(),
            any(RowMapper.class),
            anyString(),
            any(LocalDateTime.class)
        )).thenThrow(new org.springframework.dao.EmptyResultDataAccessException(1));

        IdempotentRecord result = storage.get("non-existent-key");

        // Spring 标准"无记录"信号(EmptyResult)被转换为 null,符合"null ⟹ 无记录"契约
        assertNull(result);
    }

    @Test
    @DisplayName("SQL 执行异常上抛而不是包装为 null(异常 ≠ 无记录)")
    void testGetWithSqlExceptionPropagates() {
        when(jdbcTemplate.queryForObject(
            anyString(),
            any(RowMapper.class),
            eq("sql-error-key"),
            any(LocalDateTime.class)
        )).thenThrow(new org.springframework.dao.DataAccessException("DB connection lost") {});

        // DataAccessException 不是"无记录",不允许包装成 null
        assertThrows(org.springframework.dao.DataAccessException.class,
                () -> storage.get("sql-error-key"));
    }

    @Test
    @DisplayName("保存记录-插入新记录")
    void testSaveNewRecord() {
        IdempotentRecord record = createTestRecord();
        
        when(jdbcTemplate.update(
            anyString(),
            any(), any(), any(), any(), any(), any(), any()
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

        when(jdbcTemplate.update(
            anyString(),
            any(), any(), any(), any(), any(), any(), any()
        )).thenReturn(1);

        assertDoesNotThrow(() -> storage.save(record, 3600));
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
