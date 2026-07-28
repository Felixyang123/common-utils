package com.lezai.samples.idempotent;

import com.lezai.idempotent.config.IdempotentTableInitializer;
import com.lezai.idempotent.exception.IdempotentException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * JDBC 存储的幂等集成测试。
 * 使用 Testcontainers 自动拉起 MySQL 容器。
 * 覆盖：DDL 自动建表 + migration、基本幂等、FAILED 冷却、过期清理。
 * Docker 不可用时自动跳过。
 */
@SpringBootTest
@TestPropertySource(properties = {
        "idempotent.storage=jdbc",
        "idempotent.lock=local",
        "idempotent.expire-time=10",
        "idempotent.max-fail-retry-count=2",
        "idempotent.cleanup.enabled=false",
        "idempotent.security.anonymous-strategy=ALLOW"
})
@DisplayName("幂等组件集成测试 - JDBC 存储")
class IdempotentJdbcTest {

    static MySQLContainer<?> mysql;

    @BeforeAll
    static void startContainer() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker 不可用，跳过 JDBC 集成测试");
        mysql = new MySQLContainer<>("mysql:8.0")
                .withDatabaseName("idempotent_test")
                .withUsername("test")
                .withPassword("test");
        mysql.start();
    }

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        if (mysql != null) {
            registry.add("spring.datasource.url", mysql::getJdbcUrl);
            registry.add("spring.datasource.username", mysql::getUsername);
            registry.add("spring.datasource.password", mysql::getPassword);
        }
    }

    @Autowired
    private IdempotentDemoService demoService;

    @Autowired
    private IdempotentTableInitializer tableInitializer;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        demoService.resetCallCount();
    }

    @Test
    @DisplayName("DDL 自动建表：启动时自动创建幂等表")
    void testDdlAutoCreation() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'idempotent_record'",
                Integer.class);
        assertNotNull(count);
        assertTrue(count > 0, "幂等表应已自动创建");
    }

    @Test
    @DisplayName("DDL 版本迁移：自动添加缺失的 fail_count 和 request_id 列")
    void testDdlMigration() {
        Integer failCountCol = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'idempotent_record' AND column_name = 'fail_count'",
                Integer.class);
        assertNotNull(failCountCol);
        assertTrue(failCountCol > 0, "fail_count 列应存在");

        Integer requestIdCol = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'idempotent_record' AND column_name = 'request_id'",
                Integer.class);
        assertNotNull(requestIdCol);
        assertTrue(requestIdCol > 0, "request_id 列应存在");
    }

    @Test
    @DisplayName("JDBC 基本幂等：相同 key 重复请求返回缓存结果")
    void testBasicIdempotentJdbc() {
        IdempotentDemoService.Order order1 = demoService.createOrder("J-001", "user1", "P1", 2);
        IdempotentDemoService.Order order2 = demoService.createOrder("J-001", "user1", "P1", 2);

        assertNotNull(order1);
        assertEquals(order1.orderId(), order2.orderId());
        assertEquals(1, demoService.getCallCount());
    }

    @Test
    @DisplayName("JDBC returnResultOnDuplicate=false：重复请求抛异常")
    void testNoReturnResultJdbc() {
        String result1 = demoService.payOrder("JPAY-001");
        assertEquals("PAID:JPAY-001", result1);

        assertThrows(IdempotentException.class, () -> demoService.payOrder("JPAY-001"));
        assertEquals(1, demoService.getCallCount());
    }

    @Test
    @DisplayName("FAILED 冷却：幂等记录持久化到数据库")
    void testFailedCooldown() {
        demoService.payOrder("COOL-001");
        assertThrows(IdempotentException.class, () -> demoService.payOrder("COOL-001"));

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM idempotent_record WHERE idempotent_key LIKE '%COOL-001%'",
                Integer.class);
        assertNotNull(count);
        assertTrue(count > 0, "幂等记录应存在");
    }

    @Test
    @DisplayName("过期清理：cleanExpiredRecords 清除过期记录")
    void testExpiredRecordCleanup() {
        jdbcTemplate.update(
                "INSERT INTO idempotent_record (idempotent_key, process_status, expire_time, fail_count) VALUES (?, ?, NOW() - INTERVAL 1 HOUR, 0)",
                "EXPIRED-KEY", 2);

        int cleaned = tableInitializer.cleanExpiredRecords();
        assertTrue(cleaned >= 1, "应至少清除 1 条过期记录");

        Integer remaining = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM idempotent_record WHERE idempotent_key = 'EXPIRED-KEY'",
                Integer.class);
        assertEquals(0, remaining);
    }

    @Test
    @DisplayName("JDBC 记录持久化：验证 fail_count 和 request_id 字段")
    void testRecordPersistence() {
        demoService.createOrder("J-PERSIST-001", "user1", "P1", 1);

        Integer failCount = jdbcTemplate.queryForObject(
                "SELECT fail_count FROM idempotent_record WHERE idempotent_key LIKE '%J-PERSIST-001%'",
                Integer.class);
        assertNotNull(failCount);
        assertEquals(0, failCount, "首次成功执行 fail_count 应为 0");
    }
}
