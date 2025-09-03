package com.lezai.samples.cache.sync;

import jakarta.annotation.Nullable;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
public class CacheMessagePubSub implements SmartLifecycle {
    private final List<CacheMessagePub> pubs;
    private final List<CacheMessageSub> subs;

    @Getter
    private static CacheMessagePubSub instance;

    public CacheMessagePubSub(List<CacheMessagePub> pubs, List<CacheMessageSub> subs) {
        this.pubs = Optional.ofNullable(pubs).orElse(new ArrayList<>());
        this.subs = Optional.ofNullable(subs).orElse(new ArrayList<>());
    }

    public void publish(CacheSyncMessage message, @Nullable List<CacheMessagePub> pubs) {
        if (pubs != null) {
            this.pubs.addAll(pubs);
        }
        for (CacheMessagePub pub : this.pubs) {
            pub.publish(message);
        }
    }

    public void subscribe(@Nullable List<CacheMessageSub> subs) {
        if (subs != null) {
            this.subs.addAll(subs);
        }
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (CacheMessageSub sub : this.subs) {
                executor.execute(sub::subscribe);
            }
        }
    }

    @Override
    public void start() {
        subscribe(null);
        instance = this;
    }

    @Override
    public void stop() {
        for (CacheMessageSub sub : subs) {
            sub.stop();
        }
    }

    @Override
    public boolean isRunning() {
        return false;
    }
}
