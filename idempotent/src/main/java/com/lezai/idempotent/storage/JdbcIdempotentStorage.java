package com.lezai.idempotent.storage;

import com.lezai.idempotent.core.IdempotentRecord;
import com.lezai.idempotent.enums.IdempotentStatus;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;

/**
 * JDBC 存储实现
 * 遵循 MySQL 范式，不使用 JSON 存储
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
}
