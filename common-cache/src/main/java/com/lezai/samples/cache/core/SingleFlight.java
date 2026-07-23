package com.lezai.samples.cache.core;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * 单飞执行器：同一 key 的并发加载合并为一次，其余线程等待并共享结果（缓存击穿防护）。
 * 支持同一线程对同一 key 的可重入：多级缓存链路中外层已持锁时，内层直接执行，避免自等待死锁。
 */
public class SingleFlight {

    private static final class InFlight {
        final CompletableFuture<Object> future = new CompletableFuture<>();
        final Thread leader = Thread.currentThread();
    }

    private final ConcurrentMap<String, InFlight> inFlight = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public <T> T execute(String key, long waitMs, Supplier<T> action) {
        Thread current = Thread.currentThread();
        InFlight mine = new InFlight();
        InFlight existing = inFlight.putIfAbsent(key, mine);
        if (existing == null) {
            // 领导者：执行并对追随者发布结果
            try {
                T result = action.get();
                mine.future.complete(result);
                return result;
            } catch (Throwable t) {
                mine.future.completeExceptionally(t);
                throw t;
            } finally {
                inFlight.remove(key, mine);
            }
        }
        if (existing.leader == current) {
            // 可重入：同一线程对同一 key 的嵌套调用直接执行，不等待自己
            return action.get();
        }
        // 追随者：等待领导者结果并共享
        try {
            return (T) existing.future.get(waitMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new CacheDegradedException("timed out waiting in-flight load, key=" + key);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CacheDegradedException("interrupted waiting in-flight load, key=" + key);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new CacheDegradedException("in-flight load failed, key=" + key + ": " + String.valueOf(cause));
        }
    }
}