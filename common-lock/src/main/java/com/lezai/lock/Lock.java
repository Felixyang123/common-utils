package com.lezai.lock;

public interface Lock {

    boolean tryLock(String key, long leaseTime);

    void lock(String key);

    void release(String key);

    boolean heldByCurrentThread(String key);

    default boolean tryLock(String key, long timeout, long leaseTime) {
        long start = System.currentTimeMillis();
        while (!tryLock(key, leaseTime)) {
            if (timeout >= 0 && System.currentTimeMillis() - start >= timeout) {
               return false;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return true;
    }
}
