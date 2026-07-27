package com.lezai.idempotent.storage;

import com.lezai.idempotent.core.IdempotentRecord;
import com.lezai.idempotent.enums.IdempotentStatus;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;

/**
 * JDBC 存储实现。
 *
 * <p>遵循 IdempotentStorage 接口契约 (ADR-0002):get/remove/exists 在 SQL 执行
 * 失败或反序列化失败时上抛异常(让 Spring 的 DataAccessException 自然传播),
 * 由调用方决定是否 fail-fast。返回 {@code null} 唯一语义 = "幂等键不存在"。
 * 唯一合法的"无记录"信号是 queryForObject 未命中返回 null,但不应当在 catch
 * 中捕获异常并返回 null —— 那表示"我们不知道",与真正的无记录语义混淆。</p>
 */
@Slf4j
@AllArgsConstructor
public class JdbcIdempotentStorage implements IdempotentStorage {
    private static final String DEFAULT_TABLE_NAME = "idempotent_record";

    private final String tableName;
    private final JdbcTemplate jdbcTemplate;

    public JdbcIdempotentStorage(JdbcTemplate jdbcTemplate) {
        this(DEFAULT_TABLE_NAME, jdbcTemplate);
    }


    /**
     * 查询幂等记录。未命中时 queryForObject 返回 null(已约定为"无记录"语义),
     * 其他异常(SQL 失败 / 反序列化失败)上抛 {@link DataAccessException} 或
     * RuntimeException,不允许吞掉返回 null。
     */
    @Override
    public IdempotentRecord get(String key) {
        String sql = "SELECT * FROM " + tableName + " WHERE idempotent_key = ? AND expire_time > ?";
        try {
            // 未命中时 queryForObject 抛 EmptyResultDataAccessException(这是 Spring
            // 标准的"无记录"信号),我们转换为 null 以符合"null ⟹ 无记录"契约
            return jdbcTemplate.queryForObject(sql, new IdempotentRecordRowMapper(), key, LocalDateTime.now());
        } catch (EmptyResultDataAccessException e) {
            // 未命中 = 幂等键不存在(合法的 null 语义),与"存储不可用"严格区分
            return null;
        }
        // 其他 DataAccessException(SQL 失败等)自然上抛,不允许在这里吞掉
    }

    @Override
    public void save(IdempotentRecord record, long expireSeconds) {
        String sql = "INSERT INTO " + tableName + " " +
                "(idempotent_key, process_status, process_result, result_type, error_message, expire_time, duration) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE " +
                "process_status = VALUES(process_status), process_result = VALUES(process_result), " +
                "result_type = VALUES(result_type), error_message = VALUES(error_message), " +
                "update_time = VALUES(update_time), duration = VALUES(duration)";

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

    @Override
    public void remove(String key) {
        String sql = "DELETE FROM " + tableName + " WHERE idempotent_key = ?";
        jdbcTemplate.update(sql, key);
        log.debug("Record removed from database, key: {}", key);
    }

    @Override
    public boolean exists(String key) {
        String sql = "SELECT COUNT(*) FROM " + tableName + " WHERE idempotent_key = ? AND expire_time > ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, key, LocalDateTime.now());
        return count != null && count > 0;
    }

    /**
     * 行映射器
     */
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
            record.setCreateTime(rs.getTimestamp("create_time").toLocalDateTime());
            record.setUpdateTime(rs.getTimestamp("update_time").toLocalDateTime());
            record.setExpireTime(rs.getTimestamp("expire_time").toLocalDateTime());
            return record;
        }
    }
}
