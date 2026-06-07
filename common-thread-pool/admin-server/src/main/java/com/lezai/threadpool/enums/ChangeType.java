package com.lezai.threadpool.enums;

/**
 * 变更类型枚举
 * 用于标识 API Key 和线程池配置的变更操作类型
 */
public enum ChangeType {
    /**
     * 创建操作
     */
    CREATE,

    /**
     * 更新操作
     */
    UPDATE,

    /**
     * 删除操作
     */
    DELETE,

    /**
     * API Key 重新生成操作
     */
    REGENERATE
}
