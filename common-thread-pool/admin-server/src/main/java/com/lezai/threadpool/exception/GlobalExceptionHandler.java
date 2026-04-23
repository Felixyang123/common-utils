package com.lezai.threadpool.exception;

import com.lezai.threadpool.bean.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 全局异常处理器
 * 统一处理所有异常，HTTP 状态码始终返回 200
 * 业务错误通过 ApiResponse.code 区分
 */
@Slf4j
@ControllerAdvice
@ResponseBody
public class GlobalExceptionHandler {

    /**
     * 处理配置不存在异常
     * 返回 HTTP 200，业务码 404
     */
    @ExceptionHandler(ConfigNotFoundException.class)
    public ApiResponse<Void> handleConfigNotFound(ConfigNotFoundException e) {
        log.warn("Config not found: {}", e.getMessage(), e);
        return ApiResponse.error(e.getCode(), e.getMessage());
    }

    /**
     * 处理配置已存在异常
     * 返回 HTTP 200，业务码 409
     */
    @ExceptionHandler(ConfigAlreadyExistsException.class)
    public ApiResponse<Void> handleConfigAlreadyExists(ConfigAlreadyExistsException e) {
        log.warn("Config already exists: {}", e.getMessage(), e);
        return ApiResponse.error(e.getCode(), e.getMessage());
    }

    /**
     * 处理参数校验异常
     * 返回 HTTP 200，业务码 400
     */
    @ExceptionHandler(ValidationException.class)
    public ApiResponse<Void> handleValidation(ValidationException e) {
        log.warn("Validation error: {}", e.getMessage(), e);
        return ApiResponse.error(e.getCode(), e.getMessage());
    }

    /**
     * 处理存储层异常
     * 返回 HTTP 200，业务码 500（不暴露内部错误信息）
     */
    @ExceptionHandler(StorageException.class)
    public ApiResponse<Void> handleStorage(StorageException e) {
        log.error("Storage error: {}", e.getMessage(), e);
        // 不将内部错误信息暴露给客户端
        return ApiResponse.error(e.getCode(), "Internal server error");
    }

    /**
     * 处理业务异常基类
     * 返回 HTTP 200，使用异常中的业务码
     */
    @ExceptionHandler(BusinessException.class)
    public ApiResponse<Void> handleBusinessException(BusinessException e) {
        log.warn("Business exception: {}", e.getMessage(), e);
        return ApiResponse.error(e.getCode(), e.getMessage());
    }

    /**
     * 处理其他所有未捕获的异常
     * 返回 HTTP 200，业务码 500（不暴露内部错误信息）
     */
    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception e) {
        log.error("Unexpected error: {}", e.getMessage(), e);
        // 不将内部错误信息暴露给客户端
        return ApiResponse.error(500, "Internal server error");
    }
}
