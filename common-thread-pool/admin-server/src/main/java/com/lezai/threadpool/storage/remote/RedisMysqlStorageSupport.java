package com.lezai.threadpool.storage.remote;

import com.lezai.threadpool.storage.ConcurrentMapStorage;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Redisson + MySQL 存储支持基类
 * 利用 Redisson 的 RMap 提供与 ConcurrentHashMap 相同的 API 语义
 * 包括 compute() 方法保证分布式原子性
 *
 * @param <T> 存储实体类型
 */
@Slf4j
public abstract class RedisMysqlStorageSupport<T> extends ConcurrentMapStorage<T> {

    /**
     * 启动预热线程池（单线程，构造函数实例化）
     */
    private final ExecutorService warmupExecutor;

    public RedisMysqlStorageSupport(RedissonClient redissonClient, String mapName) {
        super(redissonClient.getMap(mapName));
        this.warmupExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "config-warmup-1");
            t.setDaemon(true);
            return t;
        });
        loadCache();
        log.info("Initialized RedisMysqlStorage for map: {}", mapName);
    }

    public void loadCache() {
        // 异步加载，不阻塞 Spring 启动
        CompletableFuture.runAsync(this::loadAllFromDb, warmupExecutor).whenComplete((v, t) ->
                gracefulShutdown());
    }

    public abstract void loadAllFromDb();


    /**
     * 优雅关闭辅助方法
     */
    private void gracefulShutdown() {
        if (warmupExecutor == null || warmupExecutor.isShutdown()) {
            return;
        }
        warmupExecutor.shutdown();
        try {
            if (!warmupExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                log.warn("warmupExecutor did not terminate in time, forcing shutdown");
                warmupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            log.warn("Interrupted while waiting for warmupExecutor to terminate");
            warmupExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
