package com.lezai.threadpool.util;

public interface SyncLock {

    void lock(String lockName, String key);

    void unlock(String lockName, String key);
}
