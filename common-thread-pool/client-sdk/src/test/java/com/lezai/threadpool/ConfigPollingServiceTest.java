package com.lezai.threadpool;

import com.lezai.threadpool.bean.ConfigChangeNotification;
import com.lezai.threadpool.client.ConfigOperations;
import com.lezai.threadpool.client.ConfigPollingService;
import com.lezai.threadpool.manager.ThreadPoolManager;
import org.junit.jupiter.api.*;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("ConfigPollingService wire contract")
class ConfigPollingServiceTest {

    private ConfigOperations client;
    private ThreadPoolManager manager;
    private ConfigPollingService service;

    @BeforeEach
    void setUp() {
        client = mock(ConfigOperations.class);
        manager = new ThreadPoolManager();
        service = new ConfigPollingService(client, manager, "test-app", 30000L, 0L, 0L, 1000L, 30000L, () -> false);
    }

    @AfterEach
    void tearDown() {
        manager.shutdownNow();
    }

    @Test
    @DisplayName("applyConfigs updates locally declared pool (no-create semantics)")
    void applyConfigs_updatesDeclaredPool() {
        // 先本地声明池（受认可声明渠道），服务端下发才生效
        com.lezai.threadpool.bean.ThreadPoolConfig declared = com.lezai.threadpool.bean.ThreadPoolConfig.builder()
                .poolName("order-pool").corePoolSize(2).maximumPoolSize(4)
                .keepAliveTime(60).timeUnit(TimeUnit.SECONDS).queueCapacity(100).build();
        manager.registerPool(declared);

        // 服务端下发同池的调优值
        com.lezai.threadpool.bean.ThreadPoolConfig tuned = com.lezai.threadpool.bean.ThreadPoolConfig.builder()
                .poolName("order-pool").corePoolSize(4).maximumPoolSize(8)
                .keepAliveTime(60).timeUnit(TimeUnit.SECONDS).queueCapacity(200).build();

        int applied = service.applyConfigs(java.util.List.of(tuned));

        assertEquals(1, applied);
        assertNotNull(manager.getPool("order-pool"));
        // 验证服务端调优值已覆盖
        assertEquals(4, manager.getPool("order-pool").getCorePoolSize());
    }

    @Test
    @DisplayName("applyConfigs skips undeclared pool (server push does not create pool)")
    void applyConfigs_skipsUndeclaredPool() {
        // 本地未声明的池，服务端下发也不得创建（ADR-0008）
        com.lezai.threadpool.bean.ThreadPoolConfig unknown = com.lezai.threadpool.bean.ThreadPoolConfig.builder()
                .poolName("ghost-pool").corePoolSize(2).maximumPoolSize(4)
                .keepAliveTime(60).timeUnit(TimeUnit.SECONDS).queueCapacity(100).build();

        int applied = service.applyConfigs(java.util.List.of(unknown));

        assertEquals(0, applied);
        assertNull(manager.getPool("ghost-pool"));
    }

    @Test
    @DisplayName("startup pull does not include version param")
    void startupPull_noVersionParam() throws Exception {
        when(client.pullConfigs(null)).thenReturn(null);
        service.start();
        Thread.sleep(100);
        service.stop();
        verify(client).pullConfigs(null);
    }

    @Test
    @DisplayName("subscribe notification triggers unconditional pull")
    void subscribeNotification_triggersPull() throws Exception {
        when(client.subscribe(0, 30000L)).thenReturn(
                ConfigChangeNotification.builder().appId("test-app").version(7).build());
        when(client.pullConfigs(null)).thenReturn(null);
        service.start();
        Thread.sleep(200);
        service.stop();
        verify(client, atLeastOnce()).pullConfigs(null);
    }
}