package com.lezai.threadpool.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ThreadPoolEvent")
class ThreadPoolEventTest {

    @Test
    @DisplayName("of(type, poolName, message) stamps current time and leaves appId null")
    void of_threeArg_stampsTimeAndNullAppId() {
        ThreadPoolEvent event = ThreadPoolEvent.of(ThreadPoolEventType.POOL_CREATED, "order-pool", "created");

        assertEquals(ThreadPoolEventType.POOL_CREATED, event.type());
        assertEquals("order-pool", event.poolName());
        assertNull(event.appId());
        assertEquals("created", event.message());
        assertNotNull(event.timestamp());
    }

    @Test
    @DisplayName("of(type, poolName, appId, message) carries the given appId")
    void of_fourArg_carriesAppId() {
        ThreadPoolEvent event = ThreadPoolEvent.of(ThreadPoolEventType.CONFIG_SYNCED, "order-pool", "app-1", "synced");

        assertEquals("app-1", event.appId());
    }
}
