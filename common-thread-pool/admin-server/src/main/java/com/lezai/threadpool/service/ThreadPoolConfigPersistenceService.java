package com.lezai.threadpool.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lezai.threadpool.bean.ThreadPoolConfig;
import com.lezai.threadpool.context.AdminUserContextHolder;
import com.lezai.threadpool.converter.ThreadPoolConfigConverter;
import com.lezai.threadpool.dao.entity.ThreadPoolConfigAppEntity;
import com.lezai.threadpool.dao.entity.ThreadPoolConfigEntity;
import com.lezai.threadpool.dao.rep.ThreadPoolConfigAppRep;
import com.lezai.threadpool.dao.rep.ThreadPoolConfigRep;
import com.lezai.threadpool.enums.BizType;
import com.lezai.threadpool.enums.OperateType;
import com.lezai.threadpool.pojo.bean.ThreadPoolConfigApp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ThreadPoolConfigPersistenceService {
    private final ThreadPoolConfigRep configRep;

    private final ThreadPoolConfigAppRep configAppRep;

    private final ThreadPoolConfigConverter configConverter;

    private final OperateLogService logService;

    public List<String> allAppIds() {
        return configAppRep.allAppIds();
    }

    @Transactional
    public void createAppEntry(String appId) {
        if (configAppRep.findByAppId(appId).isPresent()) return;
        ThreadPoolConfigAppEntity entity = ThreadPoolConfigAppEntity.builder()
                .appId(appId).version(0L).build();
        configAppRep.save(entity);
        log.info("Created app entry: {}", appId);
    }

    public Optional<ThreadPoolConfigApp> getConfigAppByAppId(String appId) {
        Optional<ThreadPoolConfigAppEntity> configAppEntityOptional = configAppRep.findByAppId(appId);
        return configAppEntityOptional.map(configApp -> {
            List<ThreadPoolConfigEntity> configs = configRep.findByAppId(appId);
            return configConverter.buildConfigApp(configApp, configs);
        });
    }

    public List<ThreadPoolConfig> listByAppId(String appId) {
        return configConverter.convertConfigs(configRep.findByAppId(appId));
    }

    public Optional<ThreadPoolConfig> getByAppIdAndPoolName(String appId, String poolName) {
        return configRep.findByAppIdAndPoolName(appId, poolName).map(configConverter::convertConfig);
    }

    @Transactional(rollbackFor = Exception.class)
    public ThreadPoolConfigApp upsertConfigApp(String appId, List<ThreadPoolConfig> configs) {
        ThreadPoolConfigAppEntity configApp = ensureConfigApp(appId);

        List<ThreadPoolConfigEntity> totalConfigs = configRep.findByAppId(appId);
        Map<String, ThreadPoolConfigEntity> configMap = totalConfigs.stream().collect(Collectors.toMap(
                ThreadPoolConfigEntity::getPoolName, Function.identity()));

        List<ThreadPoolConfigEntity> newConfigs = new ArrayList<>();
        List<ThreadPoolConfigEntity> updatedConfigs = new ArrayList<>();
        List<ThreadPoolConfigEntity> allConfigs = new ArrayList<>();

        for (ThreadPoolConfig config : configs) {
            ThreadPoolConfigEntity oldConfig = configMap.get(config.getPoolName());
            if (oldConfig != null) {
                configConverter.configUpdateEntity(oldConfig, config);
                updatedConfigs.add(oldConfig);
                allConfigs.add(oldConfig);
            } else {
                ThreadPoolConfigEntity newConfig = configConverter.configConvertEntity(config, appId);
                newConfigs.add(newConfig);
                allConfigs.add(newConfig);
                totalConfigs.add(newConfig);
            }
        }

        configRep.saveOrUpdateBatch(allConfigs, 100);

        log(updatedConfigs, OperateType.UPDATE);
        log(newConfigs, OperateType.CREATE);

        List<ThreadPoolConfig> configList = configConverter.convertConfigs(totalConfigs);

        return ThreadPoolConfigApp.builder().appId(appId).version(configApp.getVersion()).configs(configList).build();
    }

    private void log(List<ThreadPoolConfigEntity> configs, OperateType operateType) {
        String operator = currentOperator();
        for (ThreadPoolConfigEntity config : configs) {
            logService.log(operateType, operator, config, String.valueOf(config.getId()), BizType.THREADPOOL_CONFIG);
        }
    }

    private String currentOperator() {
        var ctx = AdminUserContextHolder.get();
        return ctx != null ? ctx.getUsername() : "system";
    }

    private ThreadPoolConfigAppEntity ensureConfigApp(String appId) {
        Optional<ThreadPoolConfigAppEntity> configAppOptional = configAppRep.findByAppId(appId);
        ThreadPoolConfigAppEntity configApp = configAppOptional.orElseGet(() ->
                ThreadPoolConfigAppEntity.builder().appId(appId).version(0L).build());

        configApp.setVersion(configApp.getVersion() + 1);
        configAppRep.saveOrUpdate(configApp);
        return configApp;
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteByAppId(String appId) {
        List<ThreadPoolConfigEntity> oldConfigs = configRep.findByAppId(appId);
        configAppRep.remove(Wrappers.<ThreadPoolConfigAppEntity>lambdaQuery().eq(ThreadPoolConfigAppEntity::getAppId, appId));
        configRep.deleteByAppId(appId);

        log(oldConfigs, OperateType.DELETE);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteByAppIdAndPoolName(String appId, String poolName) {
        configAppRep.findByAppId(appId).ifPresent(configApp ->
                configRep.findByAppIdAndPoolName(appId, poolName).ifPresent(config -> {
                    if (configRep.removeById(config)) {
                        log(List.of(config), OperateType.DELETE);
                    }
                    long leftConfigCount = configRep.countByAppId(appId);
                    if (leftConfigCount <= 0 && configAppRep.removeById(appId)) {
                        logService.log(OperateType.DELETE, currentOperator(), configApp, String.valueOf(configApp.getId()), BizType.THREADPOOL_CONFIG);
                    }

                    if (leftConfigCount > 0) {
                        configApp.setVersion(configApp.getVersion() + 1);
                        configAppRep.saveOrUpdate(configApp);
                    }
                })
        );
    }

    @Transactional(rollbackFor = Exception.class)
    public ThreadPoolConfigApp addConfigApp(String appId, List<ThreadPoolConfig> configs) {
        ThreadPoolConfigAppEntity configApp = ensureConfigApp(appId);
        List<String> poolNames = configs.stream().map(ThreadPoolConfig::getPoolName).toList();

        List<ThreadPoolConfigEntity> oldConfigs = configRep.findByAppIdAndPoolNamesIn(appId, poolNames);
        Map<String, ThreadPoolConfigEntity> configMap = oldConfigs.stream().collect(Collectors.toMap(
                ThreadPoolConfigEntity::getPoolName, Function.identity()));

        List<ThreadPoolConfigEntity> newConfigs = configs.stream().filter(config ->
                !configMap.containsKey(config.getPoolName())).map(config ->
                configConverter.configConvertEntity(config, appId)).toList();

        boolean saved = configRep.saveBatch(newConfigs, 100);

        if (saved) {
            log(newConfigs, OperateType.CREATE);
        }

        return ThreadPoolConfigApp.builder().appId(appId).version(configApp.getVersion())
                .existConfigs(configConverter.convertConfigs(oldConfigs))
                .addedConfigs(saved ? configConverter.convertConfigs(newConfigs) : List.of())
                .build();
    }

    public List<ThreadPoolConfigEntity> listDeletedConfigs() {
        return configRep.getBaseMapper().selectDeleted();
    }

    @Transactional
    public ThreadPoolConfigEntity restoreConfigByAppIdAndPoolName(String appId, String poolName) {
        ThreadPoolConfigEntity deleted = configRep.getBaseMapper().selectDeletedByAppIdAndPoolName(appId, poolName);
        if (deleted == null) {
            return null;
        }
        configRep.getBaseMapper().restore(deleted.getId());
        configAppRep.getBaseMapper().restore(deleted.getAppId());
        return deleted;
    }

    @Transactional
    public void restoreConfigsByAppId(String appId) {
        configRep.getBaseMapper().restoreByAppId(appId);
        configAppRep.getBaseMapper().restore(appId);
    }
}
