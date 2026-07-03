package com.lezai.threadpool;

import com.lezai.threadpool.bean.ThreadPoolConfig;
import com.lezai.threadpool.client.ConfigServerClient;
import com.lezai.threadpool.core.DynamicThreadPoolWrapper;
import com.lezai.threadpool.exception.PoolNotFoundException;
import com.lezai.threadpool.manager.RemoteConfigSourcePoolManager;
import org.junit.jupiter.api.*;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("RemoteConfigSourcePoolManager")
class RemoteConfigSourcePoolManagerTest {

    private ConfigServerClient client;
    private RemoteConfigSourcePoolManager poolManager;

    private ThreadPoolConfig pool(String name) {
        return ThreadPoolConfig.builder()
                .poolName(name).corePoolSize(1).maximumPoolSize(1)
                .keepAliveTime(1).timeUnit(TimeUnit.SECONDS).queueCapacity(10).build();
    }

    @BeforeEach
    void setUp() {
        client = mock(ConfigServerClient.class);
        poolManager = new RemoteConfigSourcePoolManager(client);
    }

    @AfterEach
    void tearDown() {
        poolManager.shutdownNow();
    }

    @Test
    @DisplayName("getPool does NOT auto-register to server")
    void getPoolDoesNotAutoRegister() {
        assertNull(poolManager.getPool("unknown-pool"));
        verifyNoInteractions(client);
    }

    @Test
    @DisplayName("registerPool pushes config to server AND builds locally")
    void registerPoolPushesAndBuilds() throws Exception {
        when(client.registerConfig(any())).thenReturn(pool("order-pool"));

        DynamicThreadPoolWrapper pool = poolManager.registerPool(pool("order-pool"));

        assertNotNull(pool);
        assertEquals("order-pool", pool.getPoolName());
        verify(client).registerConfig(any());
    }

    @Test
    @DisplayName("registerPools pushes configs to server AND builds locally (bootstrap)")
    void registerPoolsBuildsLocally() throws Exception {
        doNothing().when(client).registerConfigs(any());

        poolManager.registerPools(List.of(pool("pool-a"), pool("pool-b")));

        verify(client).registerConfigs(any());
        assertNotNull(poolManager.getPool("pool-a"), "pool-a should exist locally");
        assertNotNull(poolManager.getPool("pool-b"), "pool-b should exist locally");
    }

    @Test
    @DisplayName("getRequiredPool does NOT auto-register (throws instead)")
    void getRequiredPoolDoesNotAutoRegister() {
        assertThrows(PoolNotFoundException.class, () ->
                poolManager.getRequiredPool("unknown-pool"));
        verifyNoInteractions(client);
    }
}