package com.lezai.samples.cache.sync;

public interface CacheMessagePub {

    void publish(CacheSyncMessageImpl message);

    void registerNodeInfo(CacheNodeRegisterInfo nodeInfo);
}
