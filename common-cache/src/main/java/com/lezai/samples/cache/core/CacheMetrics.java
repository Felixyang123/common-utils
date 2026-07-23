package com.lezai.samples.cache.core;

/**
 * 缓存可观测性 SPI：记录命中/未命中/降级旧值服务/回源/护栏拒绝/单飞超时/同步事件。
 * <p>
 * 实现类（如 Micrometer）应保证<b>低基数</b>——禁止 key/category 作标签，
 * 仅 reason/state 等有限枚举态可作标签，避免指标基数爆炸。
 */
public interface CacheMetrics {

    /** 缓存命中。 */
    void hit();

    /** 缓存未命中（不含命中旧值）。 */
    void miss();

    /** 降级场景下服务了过期旧值（serve-stale）。 */
    void staleServed();

    /** 执行了一次回源加载。 */
    void recordLoad();

    /** 护栏拒绝回源。reason 为低基数枚举态，如 "rate-limit" / "bulkhead"。 */
    void guardRejected(String reason);

    /** 单飞等待超时。 */
    void singleFlightTimeout();

    /** 发布了一次缓存同步消息。 */
    void syncPublished();

    /** 接收到一条缓存同步消息。 */
    void syncReceived();

    /** 处理缓存同步消息失败。 */
    void syncError();
}
