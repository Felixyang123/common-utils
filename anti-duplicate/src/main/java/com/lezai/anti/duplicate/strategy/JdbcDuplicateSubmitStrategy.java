package com.lezai.anti.duplicate.strategy;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@RequiredArgsConstructor
public class JdbcDuplicateSubmitStrategy implements DuplicateSubmitStrategy {

    private final JdbcTemplate jdbcTemplate;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean tryLock(String key, int timeoutSeconds) {
        LocalDateTime newExpireTime = LocalDateTime.now().plusSeconds(timeoutSeconds);
        String expireStr = newExpireTime.format(FORMATTER);

        try {
            // 尝试插入新记录
            insertNewLock(key, expireStr);
            return true; // 首次请求成功
        } catch (DuplicateKeyException e) {
            // 唯一索引冲突 → 处理现有记录
            return handleExistingLock(key, expireStr);
        }
    }

    /**
     * 插入新锁记录
     */
    private void insertNewLock(String key, String expireStr) {
        String sql = "INSERT INTO duplicate_submit_lock (request_key, expire_time, version) VALUES (?, ?, 0)";
        jdbcTemplate.update(sql, key, expireStr);
    }

    /**
     * 处理已存在的锁记录
     */
    private boolean handleExistingLock(String key, String newExpireStr) {
        // 1. 查询现有记录（带版本号）
        LockRecord existing = findLockByKey(key);
        if (existing == null) {
            return false; // 理论上不会发生
        }

        // 2. 检查是否过期
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime currentExpire = LocalDateTime.parse(existing.expireTime, FORMATTER);

        if (currentExpire.isBefore(now)) {
            // 3. 已过期 → 尝试更新（乐观锁）
            return tryUpdateExpiredLock(key, newExpireStr, existing.version);
        } else {
            // 4. 未过期 → 拦截
            return false;
        }
    }

    /**
     * 查询现有锁记录
     */
    private LockRecord findLockByKey(String key) {
        String sql = "SELECT expire_time, version FROM duplicate_submit_lock WHERE request_key = ?";
        return jdbcTemplate.queryForObject(sql, (rs, rowNum) ->
                new LockRecord(rs.getString("expire_time"), rs.getInt("version")), key);
    }

    /**
     * 乐观锁更新过期时间
     */
    private boolean tryUpdateExpiredLock(String key, String newExpireStr, int expectedVersion) {
        String sql = "UPDATE duplicate_submit_lock SET expire_time = ?, version = ? WHERE request_key = ? AND version = ?";
        int updated = jdbcTemplate.update(sql, newExpireStr, expectedVersion + 1, key, expectedVersion);
        return updated > 0; // 更新成功返回 true
    }

    /**
     * 内部记录类
     */
    private record LockRecord(String expireTime, int version) {
    }

}