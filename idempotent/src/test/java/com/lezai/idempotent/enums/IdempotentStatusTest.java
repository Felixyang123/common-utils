package com.lezai.idempotent.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IdempotentStatus 枚举测试
 */
@DisplayName("幂等状态枚举测试")
class IdempotentStatusTest {

    @Test
    @DisplayName("根据code获取状态-PROCESSING")
    void testFromCodeProcessing() {
        IdempotentStatus status = IdempotentStatus.fromCode(1);
        assertEquals(IdempotentStatus.PROCESSING, status);
        assertEquals(1, status.getCode());
        assertEquals("执行中", status.getDesc());
    }

    @Test
    @DisplayName("根据code获取状态-SUCCEEDED")
    void testFromCodeSucceeded() {
        IdempotentStatus status = IdempotentStatus.fromCode(2);
        assertEquals(IdempotentStatus.SUCCEEDED, status);
        assertEquals(2, status.getCode());
        assertEquals("执行成功", status.getDesc());
    }

    @Test
    @DisplayName("根据code获取状态-FAILED")
    void testFromCodeFailed() {
        IdempotentStatus status = IdempotentStatus.fromCode(3);
        assertEquals(IdempotentStatus.FAILED, status);
        assertEquals(3, status.getCode());
        assertEquals("执行失败", status.getDesc());
    }

    @Test
    @DisplayName("根据无效code抛出异常")
    void testFromCodeInvalid() {
        assertThrows(IllegalArgumentException.class, () -> IdempotentStatus.fromCode(0));
        assertThrows(IllegalArgumentException.class, () -> IdempotentStatus.fromCode(4));
        assertThrows(IllegalArgumentException.class, () -> IdempotentStatus.fromCode(-1));
    }

    @Test
    @DisplayName("所有状态值验证")
    void testAllStatusCodes() {
        IdempotentStatus[] statuses = IdempotentStatus.values();
        assertEquals(3, statuses.length);
        
        for (IdempotentStatus status : statuses) {
            IdempotentStatus retrieved = IdempotentStatus.fromCode(status.getCode());
            assertEquals(status, retrieved);
        }
    }
}
