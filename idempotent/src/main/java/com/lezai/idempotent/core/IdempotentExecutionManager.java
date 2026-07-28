package com.lezai.idempotent.core;

import com.alibaba.fastjson2.JSON;
import com.lezai.idempotent.annotation.Idempotent;
import com.lezai.idempotent.config.IdempotentProperties;
import com.lezai.idempotent.enums.IdempotentStatus;
import com.lezai.idempotent.exception.IdempotentException;
import com.lezai.idempotent.exception.IdempotentExecutionException;
import com.lezai.idempotent.exception.IdempotentLockException;
import com.lezai.idempotent.exception.IdempotentStorageException;
import com.lezai.idempotent.lock.IdempotentLockProvider;
import com.lezai.idempotent.storage.IdempotentStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;

import org.aspectj.lang.reflect.MethodSignature;

import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

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
     * 执行幂等方法。
     *
     * <p>锁调用合约 (caller-of-lock invariant):进入 handleFirstRequest / invoke 之前,
     * 必须已经持有 key 的互斥锁。通过 assert(heldByCurrentThread) 强化此合约,
     * 开启断言时任何违规调用都会立刻暴露。</p>
     */
    public Object execute(ProceedingJoinPoint joinPoint, Idempotent idempotent) {
        // 1. 解析幂等键
        String key = keyResolver.resolve(joinPoint, idempotent);
        log.debug("Idempotent key resolved: {}", key);

        // 2. 查询幂等记录 (异常 = 存储不可用,fail-fast → 503 + Retry-After)
        final IdempotentRecord record;
        try {
            record = storage.get(key);
        } catch (Exception e) {
            throw new IdempotentStorageException(
                    "Idempotent storage unavailable, key=" + key + ", cannot determine idempotency", e);
        }

        if (record != null) {
            // 记录已存在,处理重复请求
            return handleExistingRecord(joinPoint, key, record, idempotent, 0);
        }

        return handleConcurrentRequest(joinPoint, idempotent, key);
    }

    private Object handleConcurrentRequest(ProceedingJoinPoint joinPoint, Idempotent idempotent, String key) {
        // 3. 记录不存在，尝试获取锁
        boolean locked = lockProvider.tryLock(key, idempotent.lockLeaseTime());

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
        boolean locked = lockProvider.heldByCurrentThread(key) || lockProvider.tryLock(key, idempotent.lockLeaseTime());

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
                return handleSucceededRecord(key, record, idempotent, joinPoint);
            }

            // FAILED 冷却：超过 maxFailRetryCount 后不再重执行，直接返回上次错误
            if (IdempotentStatus.FAILED.equals(record.getStatus())) {
                int failCount = record.getFailCount() != null ? record.getFailCount() : 0;
                if (failCount >= properties.getMaxFailRetryCount()) {
                    log.warn("FAILED 冷却命中：key={}, failCount={}, maxFailRetryCount={}，不再重执行",
                             key, failCount, properties.getMaxFailRetryCount());
                    throw new IdempotentException(
                            "该幂等键已连续失败 " + failCount + " 次，不再重执行。上次错误: "
                            + record.getErrorMessage() + "。等待 TTL 过期后自动恢复，或手动清除记录");
                }
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

    /**
     * 首次请求处理。
     *
     * <p>锁调用合约:调用者必须持有 key 的互斥锁。直接绕过锁调用本方法会
     * 导致并发请求进入执行业务,打破幂等保护。</p>
     */
    private Object handleFirstRequest(ProceedingJoinPoint joinPoint, Idempotent idempotent, String key) {
        // ★ 调用方必须持有锁 —— 强化"caller-of-lock"合约
        assert lockProvider.heldByCurrentThread(key)
                : "Caller must hold lock before invoking handleFirstRequest, key=" + key;

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
                    handleSucceededRecord(key, record, idempotent, joinPoint);
        };
    }

    /**
     * 处理正在执行中的记录
     */
    private Object handleProcessingRecord(ProceedingJoinPoint joinPoint, String key, Idempotent idempotent, int retryCount) {
        // 锁即租约：锁已死 = 执行者已崩溃，走接管路径
        if (!lockProvider.isLocked(key)) {
            log.warn("PROCESSING 记录的锁已失效（执行者可能已崩溃），走接管路径, key: {}", key);
            return handleFailRecord(joinPoint, idempotent, key);
        }

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
                // 记录在重试等待期间过期,重新走 handleConcurrentRequest 拿锁
                // 注意:不能直接调用 handleFirstRequest,后者是 private helper,
                // 必须在持有锁的上下文调用,否则并发请求会绕过互斥直接执行业务
                log.warn("Record expired during retry for key: {}, re-acquiring lock", key);
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
    private Object handleSucceededRecord(String key, IdempotentRecord record, Idempotent idempotent, ProceedingJoinPoint joinPoint) {
        log.info("Duplicate successful request detected, key: {}", key);

        if (idempotent.returnResultOnDuplicate()) {
            if (record.getResultType() == null || record.getResultType().isEmpty()) {
                // void 方法或 storeResult=false 时 resultType 为 null
                log.info("No cached result available (resultType is null), key: {}", key);
                return null;
            }
            try {
                assertResultTypeAllowed(record.getResultType());
                // 优先用方法签名的泛型返回类型（保留 List<Order> 等泛型信息）
                MethodSignature signature = (MethodSignature) joinPoint.getSignature();
                Type returnType = signature.getMethod().getGenericReturnType();
                return JSON.parseObject(record.getResult(), returnType);
            } catch (IdempotentExecutionException e) {
                throw e;
            } catch (Exception e) {
                log.error("Failed to deserialize cached result, key: {}, resultType: {}",
                          key, record.getResultType());
                throw new IdempotentExecutionException("Failed to deserialize cached result", e);
            }
        } else {
            // 抛出异常
            throw new IdempotentException("Duplicate request detected: " + key);
        }
    }

    /**
     * 校验 resultType 是否在白名单内（防反序列化 RCE）。
     * 白名单为空时放行但首次调用 WARN。
     */
    private static volatile boolean whitelistWarned = false;

    private void assertResultTypeAllowed(String resultType) {
        List<String> whitelist = properties.getSecurity().getResultTypeWhitelist();
        if (whitelist == null || whitelist.isEmpty()) {
            if (!whitelistWarned) {
                whitelistWarned = true;
                log.warn("resultType 白名单未配置，存在反序列化风险。建议配置 idempotent.security.result-type-whitelist");
            }
            return;
        }
        for (String prefix : whitelist) {
            if (resultType.startsWith(prefix)) {
                return;
            }
        }
        throw new IdempotentExecutionException(
                "resultType 不在白名单内: " + resultType + "，白名单前缀: " + whitelist);
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

                boolean locked = lockProvider.tryLock(key, idempotent.lockLeaseTime());
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
        // 累加失败次数（用于 FAILED 冷却判断）
        record.setFailCount(record.getFailCount() == null ? 1 : record.getFailCount() + 1);
        record.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds)); // rolling TTL

        storage.save(record, expireSeconds);
        log.error("Idempotent method execution failed, key: {}, duration: {}ms, failCount: {}",
                  record.getKey(), duration, record.getFailCount());
    }

    /**
     * 保存成功记录
     */
    private void saveSuccessRecord(IdempotentRecord record, Object result, long duration, long expireSeconds, boolean storeResult) {
        record.setStatus(IdempotentStatus.SUCCEEDED);
        record.setDuration(duration);
        record.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds)); // rolling TTL

        if (storeResult && result != null) {
            record.setResult(JSON.toJSONString(result));
            record.setResultType(result.getClass().getName());
        }

        storage.save(record, expireSeconds);
    }
}
