package com.lezai.threadpool.storage.listener;

@FunctionalInterface
public interface ConfigChangeListener {
    /**
     * 配置变更回调
     *
     * @param appId   应用 ID
     * @param version 新版本号
     */
    void onConfigChanged(String appId, long version);

    /**
     * 判断监听器是否过期
     * @return
     */
    default boolean isExpired(){
        return false;
    }
}
