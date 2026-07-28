package com.lezai.idempotent.config;

import com.lezai.idempotent.exception.IdempotentException;
import com.lezai.idempotent.exception.IdempotentExecutionException;
import com.lezai.idempotent.exception.IdempotentLockException;
import com.lezai.idempotent.exception.IdempotentStorageException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * 幂等异常 → HTTP 状态码映射（补齐 ADR-0002 承诺）
 * <ul>
 *   <li>IdempotentException（重复/处理中）→ 409 Conflict</li>
 *   <li>IdempotentLockException（锁失败）→ 429 Too Many Requests + Retry-After</li>
 *   <li>IdempotentStorageException（存储不可用）→ 503 Service Unavailable + Retry-After</li>
 * </ul>
 */
@Slf4j
@RestControllerAdvice
public class IdempotentExceptionHandler {

    @ExceptionHandler(IdempotentException.class)
    public ResponseEntity<Map<String, Object>> handleIdempotentException(IdempotentException e) {
        log.warn("幂等拦截: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("code", 409, "msg", e.getMessage()));
    }

    @ExceptionHandler(IdempotentLockException.class)
    public ResponseEntity<Map<String, Object>> handleLockException(IdempotentLockException e) {
        log.warn("幂等锁失败: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", "1")
                .body(Map.of("code", 429, "msg", e.getMessage()));
    }

    @ExceptionHandler(IdempotentStorageException.class)
    public ResponseEntity<Map<String, Object>> handleStorageException(IdempotentStorageException e) {
        log.error("幂等存储不可用", e);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header("Retry-After", "1")
                .body(Map.of("code", 503, "msg", "Idempotent storage unavailable, please retry"));
    }

    @ExceptionHandler(IdempotentExecutionException.class)
    public ResponseEntity<Map<String, Object>> handleExecutionException(IdempotentExecutionException e) {
        log.error("幂等执行异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("code", 500, "msg", e.getMessage()));
    }
}
