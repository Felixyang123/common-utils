package com.lezai.lock.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MethodLock {
    /**
     * 锁的键，支持SpEL表达式
     * @return 锁的键
     */
    String key() default "";
    
    /**
     * 锁类型，默认为阻塞等待
     * @return 锁类型
     */
    LockType type() default LockType.QUEUED;
    
    /**
     * 尝试获取锁的超时时间（毫秒），仅在QUEUED模式下有效
     * @return 超时时间
     */
    long timeout() default 0;
    
    enum LockType {
        /**
         * 阻塞等待获取锁
         */
        QUEUED,
        /**
         * 尝试一次获取锁，失败则直接返回
         */
        ONCE
    }
}
