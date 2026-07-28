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
import java.util.ArrayList;
import java.util.List;

/**
 * 幂等性表自动初始化器
 * 遵循 Spring Boot 自动配置规范
 * <p>
 * 功能：
 * 1. 自动检测表是否存在
 * 2. 根据配置决定是否自动创建表
 * 3. 支持自定义表名
 * 4. DDL 版本迁移：检测缺失列并自动 ALTER TABLE ADD COLUMN
 * </p>
 */
@Slf4j
@RequiredArgsConstructor
public class IdempotentTableInitializer {

    private static final String DEFAULT_DDL_PATH = "sql/ddl.sql";

    /** 当前 schema 期望的所有列（用于版本迁移） */
    private static final String[][] EXPECTED_COLUMNS = {
            {"fail_count", "INT NOT NULL DEFAULT 0 COMMENT '失败次数'"},
            {"request_id", "VARCHAR(64) COMMENT '请求ID（用于追踪）'"},
    };

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final IdempotentProperties properties;

    /**
     * 初始化幂等表
     * 如果表不存在且配置了自动创建，则执行 DDL；
     * 如果表已存在，执行版本迁移（补缺失列）。
     */
    public void initialize() {
        String tableName = properties.getJdbc().getTableName();

        if (tableExists(tableName)) {
            log.info("Idempotent table '{}' already exists, checking schema migration", tableName);
            migrateSchema(tableName);
            return;
        }

        if (!properties.getJdbc().isAutoCreateTable()) {
            log.warn("Idempotent table '{}' does not exist and auto-create is disabled", tableName);
            return;
        }

        createTable(tableName);
    }

    /**
     * 检查表是否存在
     */
    private boolean tableExists(String tableName) {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            String catalog = connection.getCatalog();
            String schema = connection.getSchema();

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
     * 检查列是否存在
     */
    private boolean columnExists(String tableName, String columnName) {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            String catalog = connection.getCatalog();
            String schema = connection.getSchema();

            if (!StringUtils.hasText(schema)) {
                schema = null;
            }

            try (ResultSet columns = metaData.getColumns(catalog, schema, tableName, columnName)) {
                return columns.next();
            }
        } catch (SQLException e) {
            log.error("Failed to check column existence: {}.{}", tableName, columnName, e);
            return false;
        }
    }

    /**
     * DDL 版本迁移：检查期望列是否存在，缺失则自动 ALTER TABLE ADD COLUMN
     */
    private void migrateSchema(String tableName) {
        List<String> missingColumns = new ArrayList<>();
        for (String[] colDef : EXPECTED_COLUMNS) {
            if (!columnExists(tableName, colDef[0])) {
                missingColumns.add(colDef[0]);
                String alterSql = "ALTER TABLE " + tableName + " ADD COLUMN " + colDef[0] + " " + colDef[1];
                log.info("Migrating schema: adding column {}.{}", tableName, colDef[0]);
                try {
                    jdbcTemplate.execute(alterSql);
                } catch (Exception e) {
                    log.error("Failed to add column {}.{}: {}", tableName, colDef[0], e.getMessage());
                    throw new RuntimeException("Schema migration failed for " + tableName + "." + colDef[0], e);
                }
            }
        }
        if (missingColumns.isEmpty()) {
            log.info("Idempotent table '{}' schema is up to date", tableName);
        } else {
            log.info("Idempotent table '{}' schema migrated, added columns: {}", tableName, missingColumns);
        }
    }

    /**
     * 创建表
     */
    private void createTable(String tableName) {
        log.info("Creating idempotent table: {}", tableName);

        try {
            // 方式1: 使用 DDL 脚本文件
            ClassPathResource ddlResource = new ClassPathResource(DEFAULT_DDL_PATH);

            if (ddlResource.exists()) {
                ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
                populator.addScript(ddlResource);
                populator.setContinueOnError(false);
                populator.execute(dataSource);
                log.info("Successfully created idempotent table '{}' using DDL script", tableName);
            } else {
                // 方式2: 如果 DDL 文件不存在，使用内联 SQL
                createTableWithInlineSql(tableName);
            }
        } catch (Exception e) {
            log.error("Failed to create idempotent table: {}", tableName, e);
            throw new RuntimeException("Failed to create idempotent table", e);
        }
    }

    /**
     * 使用内联 SQL 创建表（当 DDL 文件不存在时的备用方案）
     */
    private void createTableWithInlineSql(String tableName) {
        log.info("Creating table '{}' using inline SQL", tableName);

        String createTableSql = buildCreateTableSql(tableName);
        jdbcTemplate.execute(createTableSql);

        log.info("Successfully created idempotent table '{}' using inline SQL", tableName);
    }

    /**
     * 构建建表 SQL（与 ddl.sql 保持同步）
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
                fail_count INT NOT NULL DEFAULT 0 COMMENT '失败次数',
                request_id VARCHAR(64) COMMENT '请求ID（用于追踪）',
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
     * 清理过期的幂等记录（分批删除，避免大事务锁表）
     *
     * @return 清理的记录数
     */
    public int cleanExpiredRecords() {
        String tableName = properties.getJdbc().getTableName();
        int batchSize = properties.getCleanup().getBatchSize();
        String sql = "DELETE FROM " + tableName + " WHERE expire_time < NOW() LIMIT " + batchSize;

        int totalDeleted = 0;
        int deletedCount;
        do {
            deletedCount = jdbcTemplate.update(sql);
            totalDeleted += deletedCount;
        } while (deletedCount >= batchSize);

        if (totalDeleted > 0) {
            log.info("Cleaned {} expired idempotent records", totalDeleted);
        }

        return totalDeleted;
    }

    /**
     * 获取表统计信息
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
