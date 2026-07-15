package com.lezai.threadpool;

import com.lezai.threadpool.bean.AddConfigAppResult;
import com.lezai.threadpool.bean.ThreadPoolConfig;
import com.lezai.threadpool.client.ConfigOperations;
import com.lezai.threadpool.core.DynamicThreadPoolWrapper;
import com.lezai.threadpool.exception.PoolNotFoundException;
import com.lezai.threadpool.manager.RemoteConfigSourcePoolManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RemoteConfigSourcePoolManager")
class RemoteConfigSourcePoolManagerTest {

    private ConfigOperations client;
    private RemoteConfigSourcePoolManager poolManager;

    private ThreadPoolConfig pool(String name) {
        return ThreadPoolConfig.builder()
                .poolName(name).corePoolSize(1).maximumPoolSize(1)
                .keepAliveTime(1).timeUnit(TimeUnit.SECONDS).queueCapacity(10).build();
    }

    @BeforeEach
    void setUp() {
        client = mock(ConfigOperations.class);
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
    @DisplayName("registerPool pushes config to server (batch endpoint) AND builds locally")
    void registerPoolPushesAndBuilds() throws Exception {
        when(client.registerConfig(any())).thenReturn(AddConfigAppResult.builder().build());

        DynamicThreadPoolWrapper pool = poolManager.registerPool(pool("order-pool"));

        assertNotNull(pool);
        assertEquals("order-pool", pool.getPoolName());
        verify(client).registerConfig(any());
    }

    @Test
    @DisplayName("registerPools pushes configs to server AND builds locally (bootstrap)")
    void registerPoolsBuildsLocally() throws Exception {
        when(client.registerConfigs(any())).thenReturn(AddConfigAppResult.builder().build());

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

    @Test
    @DisplayName("registerPool: existConfigs aligns local config to server value")
    void registerPool_existsAlignsToServer() throws Exception {
        ThreadPoolConfig orderPool = pool("order-pool");
        when(client.registerConfig(any())).thenReturn(AddConfigAppResult.builder()
                .existConfigs(List.of(orderPool)).build());

        DynamicThreadPoolWrapper pool = poolManager.registerPool(orderPool);

        assertEquals(orderPool.getCorePoolSize(), pool.getCurrentConfig().getCorePoolSize());
    }

    @Test
    @DisplayName("registerPools: retiredConfigs reverts local config to declared value (not retiredConfig value)")
    void registerPools_retiredRevertsToLocal() throws Exception {
        ThreadPoolConfig poolA = pool("pool-a");
        ThreadPoolConfig poolB = pool("pool-b");
        ThreadPoolConfig retiredA = ThreadPoolConfig.builder()
                .poolName("pool-a").corePoolSize(99).maximumPoolSize(99)
                .keepAliveTime(1).timeUnit(TimeUnit.SECONDS).queueCapacity(10).build();

        when(client.registerConfigs(any())).thenReturn(AddConfigAppResult.builder()
                .addedConfigs(List.of(poolB))
                .retiredConfigs(List.of(retiredA)).build());

        poolManager.registerPools(List.of(poolA, poolB));

        DynamicThreadPoolWrapper pool = poolManager.getPool("pool-a");
        assertNotNull(pool);
        assertEquals(1, pool.getCorePoolSize(),
                "local config should revert to declared value (corePoolSize=1), not retiredConfig's value");
    }
}