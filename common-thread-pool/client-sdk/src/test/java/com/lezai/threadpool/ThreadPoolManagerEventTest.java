package com.lezai.threadpool;

import com.lezai.threadpool.bean.ThreadPoolConfig;
import com.lezai.threadpool.event.ThreadPoolEvent;
import com.lezai.threadpool.event.ThreadPoolEventType;
import com.lezai.threadpool.manager.ThreadPoolManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ThreadPoolManager event publishing")
class ThreadPoolManagerEventTest {

    private final List<ThreadPoolEvent> published = new ArrayList<>();
    private ThreadPoolManager manager;

    @BeforeEach
    void setUp() {
        published.clear();
        manager = new ThreadPoolManager(published::add);
    }

    @AfterEach
    void tearDown() {
        manager.shutdownNow();
    }

    private ThreadPoolConfig config(String name) {
        return ThreadPoolConfig.builder()
                .poolName(name).corePoolSize(1).maximumPoolSize(1)
                .keepAliveTime(1).timeUnit(TimeUnit.SECONDS).queueCapacity(10).build();
    }

    @Test
    @DisplayName("registerPool on a new pool publishes exactly one POOL_CREATED event")
    void registerPool_newPool_publishesPoolCreated() {
        manager.registerPool(config("evt-pool"));

        assertEquals(1, published.size());
        assertEquals(ThreadPoolEventType.POOL_CREATED, published.get(0).type());
        assertEquals("evt-pool", published.get(0).poolName());
    }

    @Test
    @DisplayName("registerPool on an already-registered pool publishes nothing")
    void registerPool_existingPool_publishesNothing() {
        manager.registerPool(config("evt-pool"));
        published.clear();

        manager.registerPool(config("evt-pool"));

        assertTrue(published.isEmpty(), "re-registering an existing pool must not fire POOL_CREATED again");
    }

    @Test
    @DisplayName("updatePool publishes CONFIG_CHANGED")
    void updatePool_publishesConfigChanged() {
        manager.registerPool(config("evt-pool"));
        published.clear();

        ThreadPoolConfig updated = ThreadPoolConfig.builder()
                .poolName("evt-pool").corePoolSize(1).maximumPoolSize(2)
                .keepAliveTime(1).timeUnit(TimeUnit.SECONDS).queueCapacity(10).build();
        manager.updatePool(updated);

        assertEquals(1, published.size());
        assertEquals(ThreadPoolEventType.CONFIG_CHANGED, published.get(0).type());
        assertEquals("evt-pool", published.get(0).poolName());
    }

    @Test
    @DisplayName("removePool publishes POOL_DESTROYED")
    void removePool_publishesPoolDestroyed() {
        manager.registerPool(config("evt-pool"));
        published.clear();

        manager.removePool("evt-pool");

        assertEquals(1, published.size());
        assertEquals(ThreadPoolEventType.POOL_DESTROYED, published.get(0).type());
        assertEquals("evt-pool", published.get(0).poolName());
    }

    @Test
    @DisplayName("removePool on a missing pool publishes nothing")
    void removePool_missingPool_publishesNothing() {
        manager.removePool("never-existed");

        assertTrue(published.isEmpty());
    }

    @Test
    @DisplayName("default no-arg constructor never throws even though nothing observes events")
    void noArgConstructor_doesNotThrow() {
        ThreadPoolManager defaultManager = new ThreadPoolManager();
        assertDoesNotThrow(() -> defaultManager.registerPool(config("no-listener-pool")));
        defaultManager.shutdownNow();
    }

    @Test
    @DisplayName("upsertPool on a new pool publishes POOL_CREATED, not CONFIG_CHANGED")
    void upsertPool_newPool_publishesPoolCreated() {
        manager.upsertPool(config("upsert-evt-pool"));

        assertEquals(1, published.size());
        assertEquals(ThreadPoolEventType.POOL_CREATED, published.get(0).type());
    }

    @Test
    @DisplayName("upsertPool on an existing pool publishes CONFIG_CHANGED, not POOL_CREATED")
    void upsertPool_existingPool_publishesConfigChanged() {
        manager.upsertPool(config("upsert-evt-pool"));
        published.clear();

        ThreadPoolConfig updated = ThreadPoolConfig.builder()
                .poolName("upsert-evt-pool").corePoolSize(1).maximumPoolSize(2)
                .keepAliveTime(1).timeUnit(TimeUnit.SECONDS).queueCapacity(10).build();
        manager.upsertPool(updated);

        assertEquals(1, published.size());
        assertEquals(ThreadPoolEventType.CONFIG_CHANGED, published.get(0).type());
    }
}
