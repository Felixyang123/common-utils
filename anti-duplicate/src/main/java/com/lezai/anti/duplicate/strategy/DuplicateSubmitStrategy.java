package com.lezai.anti.duplicate.strategy;

public interface DuplicateSubmitStrategy {
    boolean tryLock(String key, int expireSeconds);

    default void unlock(String key) {
    } // 用于可选的显式解锁
}