package com.lezai.threadpool.util;

import java.util.concurrent.TimeUnit;

public interface SyncLock {

    void lock(String lockName, String key);

    /**
     * 尝试在 leaseTime 后强制释放的锁，不续期（与 lock() 的 watchdog 续期行为不同）。
     * <p>
     * 适用于"抢锁跑定时任务、超时让位"语义（如 StatsAggregationService）。
     * 持锁操作须在 leaseTime 内完成，否则锁易主可能导致重复执行。
     */
    boolean tryLock(String lockName, String key, long waitTime, long leaseTime, TimeUnit timeUnit) throws InterruptedException;

    void unlock(String lockName, String key);
}
