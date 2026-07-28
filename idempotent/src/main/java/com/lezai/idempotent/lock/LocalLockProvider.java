package com.lezai.idempotent.lock;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 本地锁提供者
 *
 * <p>基于 {@link ConcurrentHashMap#compute} 原子化 API 实现 per-key 互斥锁,
 * 依赖 CHM 对同一 key 的 {@code compute} 调用串行化保证,避免"检查锁状态
 * + 释放锁 + 删 CHM entry"非原子操作导致的竞斥击穿。</p>
 *
 * <p>不可变 {@link Entry}(ownerThreadId + lockCount) 在 {@code compute} lambda
 * 内整体替换,调用者永远看不到 lockCount 与 ownerThreadId 撕裂的中间态。
 * unlock 时 lockCount 减至 0 直接 {@code return null},CHM 自动原子删除 entry,
 * 无需独立 clean 方法。参见 ADR-0001。</p>
 */
@Slf4j
public class LocalLockProvider implements IdempotentLockProvider {

    /**
     * 不可变锁记录。每次状态迁移通过 {@code new Entry(ownerThreadId, lockCount)}
     * 生成新实例并在 CHM.compute lambda 内整体写回,保证"ownerThreadId + lockCount"
     * 永远作为原子单元对外可见。
     */
    private static final class Entry {
        final long ownerThreadId;
        final int lockCount;

        Entry(long ownerThreadId, int lockCount) {
            this.ownerThreadId = ownerThreadId;
            this.lockCount = lockCount;
        }
    }

    // TODO: 当前无自动过期,存在两类风险:
    //       1. 内存泄漏 —— unlock 因代码路径异常未执行时 entry 永久残留。
    //       2. 死锁 —— unlock 内部 compute lambda 抛异常时,CHM entry 未被清除,
    //          同 key 后续请求在该 JVM 内将永远拿不到锁(即便业务线程已退出),
    //          且无超时机制打破僵局。
    //       后续可引入定期清理(基于最后访问时间)或切回 Caffeine expireAfterAccess。
    private final ConcurrentHashMap<String, Entry> lockMap = new ConcurrentHashMap<>();

    /**
     * 尝试获取指定 key 的非阻塞互斥锁。
     *
     * <p>校验与加锁在同一 CHM.compute lambda 内原子完成,不存在"看到空闲 → 被抢占
     * → 以为持有"的时间窗口(key hash 级串行化保证)。同一 ownerThreadId 重入时
     * lockCount + 1。</p>
     *
     * @param key                 锁键
     * @param waitTimeoutSeconds  本实现忽略此参数,等效 tryLock = 立即返回
     *                            (注意:与旧版 Caffeine+ReentrantLock 的阻塞 tryLock(timeout)
     *                            语义不同,可能增加并发下同一幂等键的锁获取失败率)
     * @return 是否成功获取锁(包括重入)
     */
    @Override
    public boolean tryLock(String key, long waitTimeoutSeconds) {
        final long tid = Thread.currentThread().getId();
        final boolean[] acquired = {false};

        lockMap.compute(key, (k, existing) -> {
            // key 首次出现 —— 直接占用
            if (existing == null) {
                acquired[0] = true;
                return new Entry(tid, 1);
            }
            // 已被当前线程持有 —— 重入
            if (existing.lockCount > 0 && existing.ownerThreadId == tid) {
                acquired[0] = true;
                return new Entry(tid, existing.lockCount + 1);
            }
            // 被他人持有 —— 不修改,caller 视作 false
            return existing;
        });
        return acquired[0];
    }

    /**
     * 释放锁并将 lockCount - 1。
     *
     * <p>当且仅当调用线程是当前 owner 时才允许递减;lockCount 归零时通过
     * {@code compute} lambda 直接 {@code return null},CHM 原子清除该 key,
     * 后续请求会看到一个全新的 Entry(等价于"锁从未存在")。</p>
     */
    @Override
    public void unlock(String key) {
        final long tid = Thread.currentThread().getId();

        lockMap.compute(key, (k, existing) -> {
            if (existing == null) {
                return null;                         // 已清理
            }
            if (existing.ownerThreadId != tid) {
                return existing;                     // 非当前线程持有,忽略
            }
            int newCount = existing.lockCount - 1;
            if (newCount == 0) {
                return null;                         // ★ 原子清除 entry = clean
            }
            return new Entry(tid, newCount);          // 仍持有,递减
        });
    }

    /**
     * 判断当前线程是否持有指定 key 的锁(重入也视为持有)。
     *
     * <p>读路径走 CHM.get,与 compute 路径 no happens-before 保证,但
     * heldByCurrentThread 仅用于 finally 兜底;如果 Entry 已被 unlock 清除,
     * get 返回 null → 返回 false,语义正确(已解锁)。</p>
     */
    @Override
    public boolean heldByCurrentThread(String key) {
        final long tid = Thread.currentThread().getId();
        Entry e = lockMap.get(key);
        return e != null && e.lockCount > 0 && e.ownerThreadId == tid;
    }

    /**
     * 判断指定 key 的锁是否被任意线程持有。
     *
     * <p>仅检查 CHM 中是否存在有效 entry，不限定 owner。
     * 与 {@link #heldByCurrentThread} 不同，此方法不区分持有者线程。</p>
     */
    @Override
    public boolean isLocked(String key) {
        return lockMap.containsKey(key);
    }
}
