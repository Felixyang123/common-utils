package com.lezai.threadpool.exception;

/**
 * 线程池未找到异常。
 * 当通过 {@code getRequiredPool(name)} 访问一个未经声明的池名时抛出。
 * 继承 RuntimeException —— 这是配置错误，不可恢复。
 */
public class PoolNotFoundException extends RuntimeException {

    public PoolNotFoundException(String poolName) {
        super("Thread pool not found: " + poolName);
    }
}
