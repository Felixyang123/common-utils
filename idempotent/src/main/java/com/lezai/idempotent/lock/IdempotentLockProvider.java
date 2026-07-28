package com.lezai.idempotent.lock;

/**
 * 幂等锁提供者接口
 * 独立于存储策略，专注于锁的获取和释放
 */
public interface IdempotentLockProvider {
    
    /**
     * 尝试获取锁
     * @param key 锁键
     * @param expireSeconds 过期时间（秒）
     * @return 是否成功获取锁
     */
    boolean tryLock(String key, long expireSeconds);
    
    /**
     * 释放锁
     * @param key 锁键
     */
    void unlock(String key);
    
    /**
     * 检查锁是否存在
     * @param key 锁键
     * @return 是否存在
     */
    boolean heldByCurrentThread(String key);

    /**
     * 判断指定 key 的锁是否被任意线程/实例持有。
     * 用于判定业务执行者存活性（锁即租约语义）。
     *
     * @param key 锁键
     * @return 锁被持有返回 true，未持有返回 false
     */
    boolean isLocked(String key);
}
