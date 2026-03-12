package com.lezai.idempotent.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 幂等性表自动初始化器
 * 遵循 Spring Boot 自动配置规范
 * <p>
 * 功能：
 * 1. 自动检测表是否存在
 * 2. 根据配置决定是否自动创建表
 * 3. 支持自定义表名
 * </p>
 */
@Slf4j
@RequiredArgsConstructor
public class IdempotentTableInitializer {

    private static final String DEFAULT_DDL_PATH = "sql/ddl.sql";

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final IdempotentProperties properties;

    /**
     * 初始化幂等表
     * 如果表不存在且配置了自动创建，则执行DDL
     */
    public void initialize() {
        String tableName = properties.getJdbc().getTableName();

        // 检查表是否存在
        if (tableExists(tableName)) {
            log.info("Idempotent table '{}' already exists, skip initialization", tableName);
            return;
        }

        // 检查是否配置了自动创建
        if (!properties.getJdbc().isAutoCreateTable()) {
            log.warn("Idempotent table '{}' does not exist and auto-create is disabled", tableName);
            return;
        }

        // 执行DDL创建表
        createTable(tableName);
    }

    /**
     * 检查表是否存在
     *
     * @param tableName 表名
     * @return 是否存在
     */
    private boolean tableExists(String tableName) {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            String catalog = connection.getCatalog();
            String schema = connection.getSchema();

            // 不同数据库可能需要不同的schema处理
            if (!StringUtils.hasText(schema)) {
                schema = null;
            }

            try (ResultSet tables = metaData.getTables(catalog, schema, tableName, new String[]{"TABLE"})) {
                return tables.next();
            }
        } catch (SQLException e) {
            log.error("Failed to check table existence: {}", tableName, e);
            throw new RuntimeException("Failed to check table existence", e);
        }
    }

    /**
     * 创建表
     * 使用DDL脚本执行建表语句
     *
     * @param tableName 表名
     */
    private void createTable(String tableName) {
        log.info("Creating idempotent table: {}", tableName);

        try {
            // 方式1: 使用DDL脚本文件
            ClassPathResource ddlResource = new ClassPathResource(DEFAULT_DDL_PATH);
            
            if (ddlResource.exists()) {
                ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
                populator.addScript(ddlResource);
                populator.setContinueOnError(false);
                populator.execute(dataSource);
                log.info("Successfully created idempotent table '{}' using DDL script", tableName);
            } else {
                // 方式2: 如果DDL文件不存在，使用内联SQL
                createTableWithInlineSql(tableName);
            }
        } catch (Exception e) {
            log.error("Failed to create idempotent table: {}", tableName, e);
            throw new RuntimeException("Failed to create idempotent table", e);
        }
    }

    /**
     * 使用内联SQL创建表
     * 当DDL文件不存在时的备用方案
     *
     * @param tableName 表名
     */
    private void createTableWithInlineSql(String tableName) {
        log.info("Creating table '{}' using inline SQL", tableName);

        String createTableSql = buildCreateTableSql(tableName);
        jdbcTemplate.execute(createTableSql);

        log.info("Successfully created idempotent table '{}' using inline SQL", tableName);
    }

    /**
     * 构建建表SQL
     * 使用参数化的表名
     *
     * @param tableName 表名
     * @return 建表SQL
     */
    private String buildCreateTableSql(String tableName) {
        return """
            CREATE TABLE IF NOT EXISTS %s (
                id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                idempotent_key VARCHAR(255) NOT NULL COMMENT '幂等键',
                process_status TINYINT NOT NULL COMMENT '状态：1-执行中 2-执行成功 3-执行失败',
                process_result TEXT COMMENT '执行结果（可选存储）',
                result_type VARCHAR(255) COMMENT '结果类型',
                error_message TEXT COMMENT '错误信息',
                duration BIGINT COMMENT '执行耗时（毫秒）',
                create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                expire_time DATETIME NOT NULL COMMENT '过期时间',
                UNIQUE KEY uk_idempotent_key (idempotent_key),
                INDEX idx_expire_time (expire_time),
                INDEX idx_create_time (create_time)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='幂等性记录表'
            """.formatted(tableName);
    }

    /**
     * 清理过期的幂等记录
     * 可由定时任务调用
     *
     * @return 清理的记录数
     */
    public int cleanExpiredRecords() {
        String tableName = properties.getJdbc().getTableName();
        String sql = "DELETE FROM " + tableName + " WHERE expire_time < NOW()";

        int deletedCount = jdbcTemplate.update(sql);
        if (deletedCount > 0) {
            log.info("Cleaned {} expired idempotent records", deletedCount);
        }

        return deletedCount;
    }

    /**
     * 获取表统计信息
     *
     * @return 统计信息字符串
     */
    public String getTableStatistics() {
        String tableName = properties.getJdbc().getTableName();

        try {
            Long totalCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + tableName, Long.class);
            Long processingCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + tableName + " WHERE process_status = 1", Long.class);
            Long succeededCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + tableName + " WHERE process_status = 2", Long.class);
            Long failedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + tableName + " WHERE process_status = 3", Long.class);

            return String.format(
                "Idempotent table '%s' statistics: total=%d, processing=%d, succeeded=%d, failed=%d",
                tableName, totalCount, processingCount, succeededCount, failedCount
            );
        } catch (Exception e) {
            log.error("Failed to get table statistics", e);
            return "Failed to get table statistics: " + e.getMessage();
        }
    }
}
