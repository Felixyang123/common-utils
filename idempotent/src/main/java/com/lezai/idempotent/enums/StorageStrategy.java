package com.lezai.idempotent.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 存储策略枚举
 */
@Getter
@AllArgsConstructor
public enum StorageStrategy {
    /**
     * 本地内存存储
     * 适用场景：单机环境、开发测试
     */
    LOCAL("local"),
    
    /**
     * Redis 存储
     * 适用场景：分布式环境、生产环境
     */
    REDIS("redis"),
    
    /**
     * 数据库存储
     * 适用场景：需要持久化、审计要求
     */
    JDBC("jdbc");
    
    private final String name;
}
