package com.lezai.threadpool.storage;

import com.lezai.threadpool.bean.ThreadPoolAppConfig;
import com.lezai.threadpool.bean.ThreadPoolConfig;
import com.lezai.threadpool.storage.listener.ConfigChangeListener;

import java.util.List;
import java.util.Map;
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
    void saveConfigs(String appId, List<ThreadPoolConfig> configs);

    /**
     * 保存单个线程池配置
     *
     * @param appId  应用 ID
     * @param config 配置
     */
    void saveConfig(String appId, ThreadPoolConfig config);

    /**
     * 获取应用的所有线程池配置
     *
     * @param appId 应用 ID
     * @return 配置列表
     */
    List<ThreadPoolConfig> getConfigs(String appId);

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
     * 获取所有应用的配置
     *
     * @return 应用 ID 到配置列表的映射
     */
    Map<String, List<ThreadPoolConfig>> getAllConfigs();

    /**
     * 获取配置版本
     *
     * @param appId 应用 ID
     * @return 配置版本
     */
    long getConfigVersion(String appId);

    /**
     * 注册配置变更监听器
     *
     * @param appId    应用 ID
     * @param listener 监听器
     */
    void registerChangeListener(String appId, ConfigChangeListener listener);

    /**
     * 获取应用完整配置
     *
     * @param appId
     * @return
     */
    Optional<ThreadPoolAppConfig> getAppConfig(String appId);

    /**
     * 添加配置，不存在则保存
     *
     * @param appId
     * @param config
     * @return
     */
    ThreadPoolConfig addConfig(String appId, ThreadPoolConfig config);

    /**
     * 批量添加配置，不存在则保存
     *
     * @param appId
     * @param configs 新增的配置
     * @return
     */
    List<ThreadPoolConfig> addConfigs(String appId, List<ThreadPoolConfig> configs);
}
