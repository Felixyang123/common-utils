package com.lezai.idempotent.config;

import com.lezai.idempotent.enums.LockStrategy;
import com.lezai.idempotent.enums.StorageStrategy;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

/**
 * 幂等性配置属性
 */
@Data
@Validated
@ConfigurationProperties(prefix = "idempotent")
public class IdempotentProperties {

    /**
     * 是否启用幂等性控制
     */
    private boolean enabled = true;

    /**
     * 存储策略（默认 Redis，多实例为主）
     */
    private StorageStrategy storage = StorageStrategy.REDIS;

    /**
     * 锁策略（默认 Redis，多实例为主）
     */
    private LockStrategy lock = LockStrategy.REDIS;

    /**
     * 默认过期时间（秒）
     */
    @Min(1)
    private long expireTime = 3600;

    /**
     * FAILED 记录最大重试次数（超过后不再重执行业务，直接返回上次错误）
     */
    @Min(1)
    private int maxFailRetryCount = 3;

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

    /**
     * 安全配置
     */
    private SecurityConfig security = new SecurityConfig();

    /**
     * JDBC 过期记录清理配置
     */
    private CleanupConfig cleanup = new CleanupConfig();

    @Data
    public static class LocalConfig {
        /**
         * 最大缓存数量
         */
        @Min(1)
        private int maxSize = 10000;
    }

    @Data
    public static class RedisConfig {
        /**
         * 键前缀（存储层命名空间）
         */
        @NotBlank
        private String keyPrefix = "idempotent:";
    }

    @Data
    public static class JdbcConfig {
        /**
         * 表名
         */
        @NotBlank
        @Pattern(regexp = "^[a-zA-Z_][a-zA-Z0-9_]{0,127}$",
                 message = "表名仅允许字母、数字、下划线，且以字母或下划线开头，最长 128 字符")
        private String tableName = "idempotent_record";

        /**
         * 是否自动创建表
         */
        private boolean autoCreateTable = true;
    }

    @Data
    public static class SecurityConfig {
        /**
         * 反序列化结果类型白名单（包前缀）。
         * 非空时严格校验 resultType 是否以白名单任一前缀开头；
         * 为空时放行但启动 WARN 提示未配置。
         * 示例: ["com.lezai."]
         */
        private List<String> resultTypeWhitelist = new ArrayList<>();

        /**
         * 匿名用户策略: REJECT（抛异常）/ ALLOW（共享匿名 key）
         */
        private String anonymousStrategy = "REJECT";
    }

    @Data
    public static class CleanupConfig {
        /**
         * 是否启用定时清理过期记录
         */
        private boolean enabled = true;

        /**
         * 每次清理批次大小
         */
        @Min(1)
        private int batchSize = 1000;

        /**
         * 清理间隔（秒）
         */
        @Min(1)
        private long intervalSeconds = 1800;
    }
}
