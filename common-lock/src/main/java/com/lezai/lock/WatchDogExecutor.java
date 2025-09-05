package com.lezai.lock;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class WatchDogExecutor implements SmartLifecycle {
    private final RedisTemplate<String, Object> redisTemplate;
    private final DelayQueue<LeaseTask> leaseTaskQueue;
    private final AtomicBoolean isShutdown = new AtomicBoolean(false);
    private final ConcurrentMap<String, LeaseTask> leaseTaskMap = new ConcurrentHashMap<>();

    public WatchDogExecutor(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.leaseTaskQueue = new DelayQueue<>();
    }

    public LeaseTask createLeaseTask(String key, String lockVal, long leaseTime) {
        LeaseTask leaseTask = new LeaseTask(this.redisTemplate, this.leaseTaskQueue, key, lockVal, leaseTime);
        String leaseTaskKey = key + ":" + lockVal;
        leaseTaskMap.putIfAbsent(leaseTaskKey, leaseTask);
        leaseTaskQueue.offer(leaseTask);
        return leaseTask;
    }

    public void removeLeaseTask(String key, String lockVal) {
        String leaseTaskKey = key + ":" + lockVal;
        LeaseTask leaseTask = leaseTaskMap.remove(leaseTaskKey);
        if (leaseTask != null) {
            leaseTask.stop();
        }
    }

    @Override
    public void start() {
        Thread leaseTaskThread = new Thread(() -> {
            while (!isShutdown.get()) {
                try {
                    log.info("WatchDogExecutor take task, thread info: {}", Thread.currentThread());
                    LeaseTask leaseTask = leaseTaskQueue.take();
                    leaseTask.run();
                } catch (InterruptedException e) {
                    log.warn("WatchDogExecutor failed, cause: ", e);
                    if (Thread.interrupted()) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        });
        leaseTaskThread.setName("lease-task-thread");
        leaseTaskThread.setDaemon(true);
        leaseTaskThread.start();
    }

    @Override
    public void stop() {
        this.isShutdown.set(true);
    }

    @Override
    public boolean isRunning() {
        return isShutdown.get();
    }

    public static class LeaseTask implements Runnable, Delayed {
        private final RedisTemplate<String, Object> redisTemplate;
        private final DelayQueue<LeaseTask> leaseTaskQueue;
        private final String key;
        private final String lockVal;
        private final long leaseTime;
        private final AtomicBoolean isRunning;
        private long nextExecTime;

        public LeaseTask(RedisTemplate<String, Object> redisTemplate,
                         DelayQueue<LeaseTask> leaseTaskQueue,
                         String key, String lockVal, long leaseTime) {
            this.redisTemplate = redisTemplate;
            this.leaseTaskQueue = leaseTaskQueue;
            this.key = key;
            this.lockVal = lockVal;
            this.leaseTime = leaseTime;
            this.isRunning = new AtomicBoolean(true);
            this.nextExecTime = System.currentTimeMillis() + leaseTime / 3;
        }

        @Override
        public void run() {
            Object value = redisTemplate.opsForValue().get(key);
            if (isRunning.get() && StringUtils.equals(lockVal, String.valueOf(value))) {
                redisTemplate.expire(key, leaseTime, TimeUnit.MILLISECONDS);
                log.info("WatchDogExecutor refresh lock, key: {}, lockVal: {}", key, lockVal);
                this.nextExecTime = System.currentTimeMillis() + leaseTime / 3;
                leaseTaskQueue.offer(this);
                return;
            }
            log.info("WatchDogExecutor lease lock failed, key: {}, lockVal: {}", key, lockVal);
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return unit.convert(this.nextExecTime - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
        }

        @Override
        public int compareTo(Delayed o) {
            return Long.compare(this.nextExecTime, ((LeaseTask) o).nextExecTime);
        }

        public void stop() {
            this.isRunning.set(false);
        }
    }
}
