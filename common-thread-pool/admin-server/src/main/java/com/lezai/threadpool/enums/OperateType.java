package com.lezai.threadpool.enums;

/**
 * 操作日志类型枚举
 */
public enum OperateType {
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
    REGENERATE,

    /**
     * 插入或更新操作
     */
    UPSERT,

    /**
     * 回滚操作
     */
    ROLLBACK
}
