package com.lezai.lock;

public interface Lock {

    boolean tryLock(String key);

    void lock(String key);

    void release(String key);
}
