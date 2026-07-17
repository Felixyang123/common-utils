package com.lezai.idempotent.core;

import com.alibaba.fastjson2.JSON;
import com.lezai.idempotent.annotation.Idempotent;
import com.lezai.idempotent.config.IdempotentProperties;
import com.lezai.idempotent.enums.IdempotentStatus;
import com.lezai.idempotent.exception.IdempotentException;
import com.lezai.idempotent.exception.IdempotentExecutionException;
import com.lezai.idempotent.exception.IdempotentLockException;
import com.lezai.idempotent.lock.IdempotentLockProvider;
import com.lezai.idempotent.storage.IdempotentStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;

import java.time.LocalDateTime;

/**
 * 幂等执行管理器
 * 核心组件：协调幂等性检查、方法执行、结果存储
 * <p>
 * 核心流程：
 * 1. 查询幂等记录
 * 2. 存在 -> 根据状态处理
 * 3. 不存在 -> 获取锁 -> 保存PROCESSING -> 执行业务 -> 保存结果
 */
@Slf4j
@RequiredArgsConstructor
public class IdempotentExecutionManager {

    private final IdempotentKeyResolver keyResolver;
    private final IdempotentStorage storage;
    private final IdempotentLockProvider lockProvider;
    private final IdempotentProperties properties;

    /**
     * 执行幂等方法
     */
    public Object execute(ProceedingJoinPoint joinPoint, Idempotent idempotent) {
        // 1. 解析幂等键
        String key = keyResolver.resolve(joinPoint, idempotent);
        log.debug("Idempotent key resolved: {}", key);

        // 2. 查询幂等记录
        IdempotentRecord record = storage.get(key);

        if (record != null) {
            // 记录已存在，处理重复请求
            return handleExistingRecord(joinPoint, key, record, idempotent, 0);
        }

        return handleConcurrentRequest(joinPoint, idempotent, key);
    }

    private Object handleConcurrentRequest(ProceedingJoinPoint joinPoint, Idempotent idempotent, String key) {
        // 3. 记录不存在，尝试获取锁
        boolean locked = lockProvider.tryLock(key, idempotent.tryLockTime());

        if (!locked) {
            // 获取锁失败，根据策略处理
            return handleLockFailure(joinPoint, key, idempotent);
        }

        try {
            // 4. 双重检查：防止并发情况下重复创建
            IdempotentRecord record = storage.get(key);
            if (record != null) {
                return handleExistingRecord(joinPoint, key, record, idempotent, 0);
            }

            // 5. 首次请求
            return handleFirstRequest(joinPoint, idempotent, key);
        } finally {
            // 9. 释放锁
            if (lockProvider.heldByCurrentThread(key)) {
                lockProvider.unlock(key);
            }
        }
    }

    private Object handleFailRecord(ProceedingJoinPoint joinPoint, Idempotent idempotent, String key) {
        // 3. 记录不存在，尝试获取锁
        boolean locked = lockProvider.heldByCurrentThread(key) || lockProvider.tryLock(key, idempotent.tryLockTime());

        if (!locked) {
            // 获取锁失败，根据策略处理
            return handleLockFailure(joinPoint, key, idempotent);
        }

        try {
            // 4. 双重检查：防止并发情况下重复创建
            IdempotentRecord record = storage.get(key);

            if (record == null) {
                return handleFirstRequest(joinPoint, idempotent, key);
            }

            if (IdempotentStatus.PROCESSING.equals(record.getStatus())) {
                return handleProcessingRecord(joinPoint, key, idempotent, 0);
            }

            if (IdempotentStatus.SUCCEEDED.equals(record.getStatus())) {
                return handleSucceededRecord(key, record, idempotent);
            }

            record.setStatus(IdempotentStatus.PROCESSING);
            storage.save(record, properties.getExpireTime());

            // 6. 执行业务逻辑
            return invoke(joinPoint, idempotent, key, record);
        } finally {
            // 9. 释放锁
            if (lockProvider.heldByCurrentThread(key)) {
                lockProvider.unlock(key);
            }
        }
    }

    private Object invoke(ProceedingJoinPoint joinPoint, Idempotent idempotent, String key, IdempotentRecord record) {
        long startTime = System.currentTimeMillis();
        Object result = null;
        Throwable exception = null;

        try {
            result = joinPoint.proceed();
        } catch (Throwable ex) {
            exception = ex;
        }

        long duration = System.currentTimeMillis() - startTime;

        // 7. 处理执行结果
        if (exception != null) {
            // 业务执行失败
            saveFailedRecord(record, exception, duration, properties.getExpireTime());
            throw new IdempotentExecutionException("Business method execution failed", exception);
        }

        // 8. 业务执行成功，保存成功记录
        saveSuccessRecord(record, result, duration, properties.getExpireTime(), idempotent.storeResult());

        logSuccess(key, duration);
        return result;
    }

    private static void logSuccess(String key, long duration) {
        log.info("Idempotent method executed successfully, key: {}, duration: {}ms", key, duration);
    }

    private Object handleFirstRequest(ProceedingJoinPoint joinPoint, Idempotent idempotent, String key) {
        // 5. 保存 PROCESSING 状态
        IdempotentRecord record = createProcessingRecord(key, properties.getExpireTime());
        storage.save(record, properties.getExpireTime());

        // 6. 执行业务逻辑
        return invoke(joinPoint, idempotent, key, record);
    }

    /**
     * 处理已存在的记录
     */
    private Object handleExistingRecord(ProceedingJoinPoint joinPoint, String key, IdempotentRecord record, Idempotent idempotent, int retryCount) {
        log.debug("Existing record found, key: {}, status: {}", key, record.getStatus());

        return switch (record.getStatus()) {
            case PROCESSING ->
                // 正在执行中
                    handleProcessingRecord(joinPoint, key, idempotent, retryCount);
            case FAILED ->
                // 执行失败
                    handleFailRecord(joinPoint, idempotent, key);
            case SUCCEEDED ->
                // 执行成功
                    handleSucceededRecord(key, record, idempotent);
        };
    }

    /**
     * 处理正在执行中的记录
     */
    private Object handleProcessingRecord(ProceedingJoinPoint joinPoint, String key, Idempotent idempotent, int retryCount) {
        if (idempotent.failFast()) {
            throw new IdempotentException("Request is processing, please retry later");
        }

        // WAIT_RETRY 策略：等待并重试
        if (retryCount >= idempotent.maxRetryCount()) {
            // 重试次数用尽
            throw new IdempotentException("Max retry count exceeded while waiting for processing result");
        }

        try {
            Thread.sleep(idempotent.retryInterval());

            IdempotentRecord record = storage.get(key);
            if (record == null) {
                // 兜底策略，理论上不会发生
                return handleConcurrentRequest(joinPoint, idempotent, key);
            }

            return handleExistingRecord(joinPoint, key, record, idempotent, ++retryCount);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IdempotentException("Interrupted while waiting for result", e);
        }

    }

    /**
     * 处理已成功执行的记录
     */
    private Object handleSucceededRecord(String key, IdempotentRecord record, Idempotent idempotent) {
        log.info("Duplicate successful request detected, key: {}", key);

        if (idempotent.returnResultOnDuplicate()) {
            // 返回缓存结果
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
            // 抛出异常
            throw new IdempotentException("Duplicate request detected: " + key);
        }
    }

    /**
     * 处理获取锁失败
     */
    private Object handleLockFailure(ProceedingJoinPoint joinPoint, String key, Idempotent idempotent) {
        if (idempotent.failFast()) {
            throw new IdempotentLockException("Failed to acquire lock: " + key);
        }

        // WAIT_RETRY 策略：等待并重试获取锁
        int maxRetries = idempotent.maxRetryCount();
        long interval = idempotent.retryInterval();

        // 重试
        for (int i = 0; i < maxRetries; i++) {
            try {
                Thread.sleep(interval);

                boolean locked = lockProvider.tryLock(key, idempotent.tryLockTime());
                if (!locked) {
                    continue;
                }

                return handleFailRecord(joinPoint, idempotent, key);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IdempotentLockException("Interrupted while waiting for lock", e);
            }
        }

        // 重试超出上限抛出异常
        throw new IdempotentLockException("Max retry count exceeded while waiting for lock: " + key);
    }

    /**
     * 创建处理中状态的记录
     */
    private IdempotentRecord createProcessingRecord(String key, long expireSeconds) {
        IdempotentRecord record = new IdempotentRecord();
        record.setKey(key);
        record.setStatus(IdempotentStatus.PROCESSING);
        record.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        return record;
    }

    /**
     * 保存失败记录
     */
    private void saveFailedRecord(IdempotentRecord record, Throwable exception, long duration, long expireSeconds) {
        record.setStatus(IdempotentStatus.FAILED);
        record.setErrorMessage(exception.getMessage());
        record.setDuration(duration);

        storage.save(record, expireSeconds);
        log.error("Idempotent method execution failed, key: {}, duration: {}ms", record.getKey(), duration);
    }

    /**
     * 保存成功记录
     */
    private void saveSuccessRecord(IdempotentRecord record, Object result, long duration, long expireSeconds, boolean storeResult) {
        record.setStatus(IdempotentStatus.SUCCEEDED);
        record.setDuration(duration);

        if (storeResult && result != null) {
            record.setResult(JSON.toJSONString(result));
            record.setResultType(result.getClass().getName());
        }

        storage.save(record, expireSeconds);
    }
}
