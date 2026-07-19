package com.lezai.threadpool.storage;

import com.lezai.threadpool.bean.ThreadPoolConfig;
import com.lezai.threadpool.bean.AddConfigAppResult;
import com.lezai.threadpool.pojo.bean.ThreadPoolAppConfig;

import java.util.List;
import java.util.Optional;

/**
 * 配置存储接口
 * 支持多应用、多线程池的配置存储
 */
public interface ConfigStorage {

    /**
     * 保存应用的所有线程池配置
     *
     * @param appId   应用 ID
     * @param configs 配置列表
     */
    default void saveConfigs(String appId, List<ThreadPoolConfig> configs) {
        for (ThreadPoolConfig config : configs) {
            saveConfig(appId, config);
        }
    }

    /**
     * 保存单个线程池配置
     *
     * @param appId  应用 ID
     * @param config 配置
     */
    void saveConfig(String appId, ThreadPoolConfig config);

    /**
     * 获取单个线程池配置
     *
     * @param appId    应用 ID
     * @param poolName 线程池名称
     * @return 配置
     */
    Optional<ThreadPoolConfig> getConfig(String appId, String poolName);

    /**
     * 删除应用的所有配置
     *
     * @param appId 应用 ID
     */
    void deleteConfigs(String appId);

    /**
     * 删除单个线程池配置
     *
     * @param appId    应用 ID
     * @param poolName 线程池名称
     */
    void deleteConfig(String appId, String poolName);

    /**
     * 获取所有应用 ID
     *
     * @return 应用 ID 列表
     */
    List<String> listAppIds();

    /**
     * 获取应用完整配置
     *
     * @param appId
     * @return
     */
    Optional<ThreadPoolAppConfig> getAppConfig(String appId);

    /**
     * 批量获取所有应用的完整配置（单次查询，避免 N+1）
     */
    List<ThreadPoolAppConfig> listAllAppConfigs();

    /**
     * 批量添加配置，不存在则保存
     *
     * @param appId
     * @param configs 新增的配置
     * @return
     */
    AddConfigAppResult addConfigs(String appId, List<ThreadPoolConfig> configs);
}
