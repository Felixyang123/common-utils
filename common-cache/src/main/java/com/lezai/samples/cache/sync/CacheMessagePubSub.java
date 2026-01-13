package com.lezai.samples.cache.sync;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@RequiredArgsConstructor
public class CacheMessagePubSub implements SmartLifecycle {
    private final CacheMessagePub pub;
    private final CacheMessageSub sub;

    @Getter
    private static CacheMessagePubSub instance;

    public void register(CacheNodeRegisterInfo registerInfo) {
        pub.registerNodeInfo(registerInfo);
    }

    public void publish(CacheSyncMessageImpl message) {
        pub.publish(message);
    }

    public void subscribe() {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            executor.execute(sub::subscribe);
        }
    }

    @Override
    public void start() {
        subscribe();
        instance = this;
    }

    @Override
    public void stop() {
        sub.stop();
    }

    @Override
    public boolean isRunning() {
        return false;
    }
}
