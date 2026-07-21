package com.lezai.threadpool.storage.listener;

import com.lezai.threadpool.utils.ExecutorUtils;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.concurrent.*;

/**
 * 监听器管理器，注册、触发、定期清理过期监听器
 */
@Component
@Slf4j
public class ConfigChangeListenerManager {
    private final ConcurrentMap<String, CopyOnWriteArrayList<ConfigChangeListener>> listenerMap = new ConcurrentHashMap<>();

    private ScheduledExecutorService cleaner;

    @PostConstruct
    public void init() {
        log.info("Initialize ConfigChangeListenerManager");
        // 定期清理过期监听器
        cleaner = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("ConfigChangeListenerCleaner");
            return t;
        });

        cleaner.scheduleWithFixedDelay(() -> {
            log.debug("Scanning for expired listeners...");
            listenerMap.entrySet().removeIf(entry -> {
                CopyOnWriteArrayList<ConfigChangeListener> listeners = entry.getValue();
                listeners.removeIf(ConfigChangeListener::isExpired);
                return listeners.isEmpty();
            });
        }, 10, 30, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void destroy() {
        log.info("Destroy ConfigChangeListenerManager");
        ExecutorUtils.shutdown(cleaner, "ConfigChangeListenerCleaner");
    }

    /**
     * 注册监听器
     *
     * @param appId
     * @param listener
     */
    public void register(String appId, ConfigChangeListener listener) {
        listenerMap.computeIfAbsent(appId, k -> new CopyOnWriteArrayList<>()).add(listener);
        log.info("Register listener for appId: {}", appId);
    }

    /**
     * 注销监听器
     */
    public void unregister(String appId) {
        unregister(appId, null);
    }

    /**
     * 注销监听器
     */
    public void unregister(String appId, ConfigChangeListener listener) {
        if (listener == null) {
            listenerMap.remove(appId);
        } else {
            listenerMap.computeIfPresent(appId, (k, listeners) -> {
                listeners.remove(listener);
                return listeners.isEmpty() ? null : listeners;
            });
        }
        log.info("Unregister listener for appId: {}", appId);
    }

    /**
     * 触发监听器，触发后删除监听器。
     * <p>
     * 调用方必须保证 version 单调递增（配置变更后 ensureConfigApp 涨版本），
     * 或语义为整 app 退管（传 {@link Long#MAX_VALUE}）。
     * 整表 remove 后回调 listener，listener 内部判 {@code newVersion > version} 决定是否 complete。
     */
    @Async("listenerNotifyExecutor")
    public void triggerListeners(String appId, long version) {
        CopyOnWriteArrayList<ConfigChangeListener> removed = listenerMap.remove(appId);
        if (removed == null) {
            return;
        }
        // 原子快照后迭代，防止并发 register() 重新创建列表后丢变更
        for (ConfigChangeListener listener : new ArrayList<>(removed)) {
            try {
                listener.onConfigChanged(appId, version);
            } catch (Exception e) {
                log.error("Error in listener for appId: {}, version: {}", appId, version, e);
            }
        }
    }
}
