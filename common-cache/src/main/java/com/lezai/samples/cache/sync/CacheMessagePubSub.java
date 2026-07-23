package com.lezai.samples.cache.sync;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 缓存同步发布门面。订阅生命周期由 RedisMessageListenerContainer 托管，本类只负责发布。
 */
@Slf4j
@RequiredArgsConstructor
public class CacheMessagePubSub {
    private final CacheMessagePub pub;

    public void register(CacheNodeRegisterInfo registerInfo) {
        pub.registerNodeInfo(registerInfo);
    }

    public void publish(CacheSyncMessageImpl message) {
        pub.publish(message);
    }
}
