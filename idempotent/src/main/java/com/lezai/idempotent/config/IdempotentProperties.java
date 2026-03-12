package com.lezai.idempotent.config;

import com.lezai.idempotent.enums.LockStrategy;
import com.lezai.idempotent.enums.StorageStrategy;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 幂等性配置属性
 */
@Data
@ConfigurationProperties(prefix = "idempotent")
public class IdempotentProperties {
    
    /**
     * 是否启用幂等性控制
     */
    private boolean enabled = true;
    
    /**
     * 默认存储策略
     */
    private StorageStrategy storage = StorageStrategy.LOCAL;

    /**
     * 默认锁策略
     */
    private LockStrategy lock = LockStrategy.LOCAL;

    /**
     * 默认过期时间（秒）
     */
    private long expireTime = 3600;
    
    /**
     * 本地存储配置
     */
    private LocalConfig local = new LocalConfig();
    
    /**
     * Redis 存储配置
     */
    private RedisConfig redis = new RedisConfig();
    
    /**
     * JDBC 存储配置
     */
    private JdbcConfig jdbc = new JdbcConfig();
    
    @Data
    public static class LocalConfig {
        /**
         * 最大缓存数量
         */
        private int maxSize = 10000;
    }
    
    @Data
    public static class RedisConfig {
        /**
         * 键前缀
         */
        private String keyPrefix = "idempotent:";
    }
    
    @Data
    public static class JdbcConfig {
        /**
         * 表名
         */
        private String tableName = "idempotent_record";
        
        /**
         * 是否自动创建表
         */
        private boolean autoCreateTable = true;
    }
}
