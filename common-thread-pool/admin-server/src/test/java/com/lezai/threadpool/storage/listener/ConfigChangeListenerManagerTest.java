package com.lezai.threadpool.storage.listener;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigChangeListenerManagerTest {

    private ConfigChangeListenerManager manager;

    @BeforeEach
    void setUp() {
        manager = new ConfigChangeListenerManager();
        manager.init();
    }

    @Test
    @DisplayName("register and trigger listener works")
    void registerAndTrigger() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicLong capturedVersion = new AtomicLong();

        manager.register("app1", (appId, version) -> {
            capturedVersion.set(version);
            latch.countDown();
        });

        manager.triggerListeners("app1", 5L);

        boolean received = latch.await(3, TimeUnit.SECONDS);
        assertThat(received).isTrue();
        assertThat(capturedVersion.get()).isEqualTo(5L);
    }

    @Test
    @DisplayName("triggerListeners with no listeners does nothing")
    void trigger_noListener() {
        manager.triggerListeners("nonexistent", 1L);
    }

    @Test
    @DisplayName("listener is removed after trigger")
    void listener_removedAfterTrigger() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        manager.register("app1", (appId, version) -> latch.countDown());
        manager.triggerListeners("app1", 1L);
        latch.await(3, TimeUnit.SECONDS);

        CountDownLatch latch2 = new CountDownLatch(1);
        manager.triggerListeners("app1", 2L);
        assertThat(latch2.await(1, TimeUnit.SECONDS)).isFalse();
    }

    @Test
    @DisplayName("unregister removes all listeners for appId")
    void unregister() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        manager.register("app1", (appId, version) -> latch.countDown());
        manager.unregister("app1");
        manager.triggerListeners("app1", 1L);

        assertThat(latch.await(1, TimeUnit.SECONDS)).isFalse();
    }

    @Test
    @DisplayName("unregister with listener removes specific listener")
    void unregister_specificListener() throws Exception {
        CountDownLatch latch1 = new CountDownLatch(1);
        CountDownLatch latch2 = new CountDownLatch(1);

        ConfigChangeListener listener1 = (appId, version) -> latch1.countDown();
        ConfigChangeListener listener2 = (appId, version) -> latch2.countDown();

        manager.register("app1", listener1);
        manager.register("app1", listener2);

        manager.unregister("app1", listener1);
        manager.triggerListeners("app1", 1L);

        assertThat(latch1.await(1, TimeUnit.SECONDS)).isFalse();
        assertThat(latch2.await(3, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    @DisplayName("exception in listener does not prevent cleanup")
    void listenerException_handled() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        manager.register("app1", (appId, version) -> {
            throw new RuntimeException("listener error");
        });
        manager.register("app1", (appId, version) -> latch.countDown());

        manager.triggerListeners("app1", 1L);

        assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
    }
}
