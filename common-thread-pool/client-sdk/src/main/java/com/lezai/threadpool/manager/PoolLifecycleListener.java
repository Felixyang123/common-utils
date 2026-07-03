package com.lezai.threadpool.manager;

import com.lezai.threadpool.core.DynamicThreadPoolWrapper;

/**
 * 池生命周期监听器：在池被创建时收到通知，携带 wrapper 引用本身
 * （区别于 {@link com.lezai.threadpool.event.ThreadPoolEventListener}，后者只携带事件元数据，
 * 不适合像 Micrometer binder 这种需要持有 wrapper 引用来注册 Gauge 的场景）。
 * <p>
 * 主要用途：{@code ThreadPoolMetricsBinder} 在 {@code bindTo()} 之后创建的池也能被动态注册指标。
 */
@FunctionalInterface
public interface PoolLifecycleListener {

    void onPoolCreated(DynamicThreadPoolWrapper pool);
}
