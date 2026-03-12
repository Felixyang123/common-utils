package com.lezai.idempotent.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 幂等性状态枚举
 */
@Getter
@AllArgsConstructor
public enum IdempotentStatus {
    /**
     * 正在执行中
     */
    PROCESSING(1, "执行中"),

    /**
     * 执行成功
     */
    SUCCEEDED(2, "执行成功"),

    /**
     * 执行失败
     */
    FAILED(3, "执行失败");

    private final int code;
    private final String desc;

    public static IdempotentStatus fromCode(int code) {
        for (IdempotentStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("Invalid status code: " + code);
    }
}
