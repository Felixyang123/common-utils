package com.lezai.lock;

public interface Lock {

    boolean tryLock(String key, long leaseTime);

    void lock(String key);

    void release(String key);

    default void lock(String key, long timeout, long leaseTime) {
        long start = System.currentTimeMillis();
        while (!tryLock(key, leaseTime)) {
            if (timeout >= 0 && System.currentTimeMillis() - start > timeout) {
                throw new RuntimeException("Lock timeout: " + key);
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                if (Thread.interrupted()) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
}
