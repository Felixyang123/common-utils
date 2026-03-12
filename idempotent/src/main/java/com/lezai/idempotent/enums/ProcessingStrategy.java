package com.lezai.idempotent.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 正在执行中的请求处理策略
 */
@Getter
@AllArgsConstructor
public enum ProcessingStrategy {
    /**
     * 等待并重试
     * 等待当前执行完成，然后获取结果
     */
    WAIT_RETRY("wait_retry"),
    
    /**
     * 快速失败
     * 直接抛出异常，不等待
     */
    FAST_FAIL("fast_fail");
    
    private final String name;
}
