package com.lezai.samples.cache.sync;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;

/**
 * Lifecycle-managed pub/sub facade for cache synchronization.
 * Subscribes on start, unsubscribes on stop.
 */
@Slf4j
@RequiredArgsConstructor
public class CacheMessagePubSub implements SmartLifecycle {
    private final CacheMessagePub pub;
    private final CacheMessageSub sub;
    private volatile boolean running = false;

    public void register(CacheNodeRegisterInfo registerInfo) {
        pub.registerNodeInfo(registerInfo);
    }

    public void publish(CacheSyncMessageImpl message) {
        pub.publish(message);
    }

    @Override
    public void start() {
        Thread.ofVirtual().name("cache-message-subscriber").start(() -> {
            try {
                sub.subscribe();
            } catch (Exception e) {
                log.error("Cache message subscriber failed", e);
            }
        });
        running = true;
    }

    @Override
    public void stop() {
        sub.stop();
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
