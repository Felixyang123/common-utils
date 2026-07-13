package com.lezai.threadpool.util;

import java.util.concurrent.TimeUnit;

public interface SyncLock {

    void lock(String lockName, String key);

    boolean tryLock(String lockName, String key, long timeout, TimeUnit unit);

    void unlock(String lockName, String key);
}
