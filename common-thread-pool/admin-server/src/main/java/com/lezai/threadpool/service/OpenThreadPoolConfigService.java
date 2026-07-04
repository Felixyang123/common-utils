package com.lezai.threadpool.service;

import com.lezai.threadpool.bean.ThreadPoolAppConfig;
import com.lezai.threadpool.bean.ThreadPoolConfig;
import com.lezai.threadpool.bean.ThreadPoolStatsReport;
import com.lezai.threadpool.exception.ConfigNotFoundException;
import com.lezai.threadpool.exception.ConfigNotModifiedException;
import com.lezai.threadpool.exception.ValidationException;
import com.lezai.threadpool.storage.ConfigStorage;
import com.lezai.threadpool.storage.StatsStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.List;

/**
 * Open API 线程池配置服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpenThreadPoolConfigService {

    private final ConfigStorage configStorage;
    private final StatsStorage statsStorage;

    /**
     * 添加配置，存在直接返回
     */
    public ThreadPoolConfig addConfig(String appId, ThreadPoolConfig config) {
        if (appId == null || appId.isBlank()) {
            throw new ValidationException("appId must not be blank");
        }
        if (config == null) {
            throw new ValidationException("config must not be null");
        }
        return configStorage.addConfig(appId, config);
    }

    /**
     * 批量添加配置
     */
    public List<ThreadPoolConfig> addConfigs(String appId, List<ThreadPoolConfig> configs) {
        if (appId == null || appId.isBlank()) {
            throw new ValidationException("appId must not be blank");
        }
        if (configs == null) {
            throw new ValidationException("configs must not be null");
        }
        return configStorage.addConfigs(appId, configs);
    }

    /**
     * 短轮询获取配置
     */
    public ThreadPoolAppConfig pullConfigs(String appId, Long version) {
        return configStorage.getAppConfig(appId).map(appConfig -> {
            if (version != null && appConfig.getConfigVersion() <= version) {
                throw new ConfigNotModifiedException("Config not modified for appId: " + appId);
            }
            return appConfig;
        }).orElseThrow(() -> new ConfigNotFoundException("Config not found for appId: " + appId));
    }

    /**
     * 接收线程池统计信息上报
     */
    public void reportStats(ThreadPoolStatsReport report) {
        if (report == null || report.getAppId() == null) {
            throw new ValidationException("Report and appId cannot be null");
        }

        log.debug("Received stats report from appId: {}, pools: {}",
                report.getAppId(),
                report.getStatsList() != null ? report.getStatsList().size() : 0);

        if (!CollectionUtils.isEmpty(report.getStatsList())) {
            statsStorage.saveStats(report.getAppId(), report.getStatsList());
            log.info("Saved stats report from appId: {}, pools: {}",
                    report.getAppId(), report.getStatsList().size());
        }
    }

}
