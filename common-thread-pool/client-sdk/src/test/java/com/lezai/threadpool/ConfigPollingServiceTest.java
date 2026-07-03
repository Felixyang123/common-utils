package com.lezai.threadpool;

import com.lezai.threadpool.bean.ConfigChangeNotification;
import com.lezai.threadpool.client.ConfigPollingService;
import com.lezai.threadpool.client.ConfigServerClient;
import com.lezai.threadpool.manager.ThreadPoolManager;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("ConfigPollingService wire contract")
class ConfigPollingServiceTest {

    private ConfigServerClient client;
    private ThreadPoolManager manager;
    private ConfigPollingService service;

    @BeforeEach
    void setUp() {
        client = mock(ConfigServerClient.class);
        manager = new ThreadPoolManager();
        service = new ConfigPollingService(client, manager, "test-app", 30000L, 0L, 1000L, 30000L);
    }

    @AfterEach
    void tearDown() {
        manager.shutdownNow();
    }

    @Test
    @DisplayName("applyConfigs uses upsertPool for each config")
    void applyConfigs_usesUpsertPool() {
        com.lezai.threadpool.bean.ThreadPoolConfig config = com.lezai.threadpool.bean.ThreadPoolConfig.builder()
                .poolName("order-pool").corePoolSize(2).maximumPoolSize(4)
                .keepAliveTime(60).timeUnit(TimeUnit.SECONDS).queueCapacity(100).build();

        int applied = service.applyConfigs(java.util.List.of(config));

        assertEquals(1, applied);
        assertNotNull(manager.getPool("order-pool"));
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