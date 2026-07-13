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
import com.lezai.threadpool.pojo.cmd.ThreadPoolConfigAppUpsertCmd;
import com.lezai.threadpool.pojo.cmd.ThreadPoolConfigUpsertCmd;
import com.lezai.threadpool.pojo.dto.AddConfigAppResultDto;
import com.lezai.threadpool.pojo.dto.ThreadPoolConfigAppDto;
import com.lezai.threadpool.pojo.dto.ThreadPoolConfigAppRefreshPreCheckDto;
import com.lezai.threadpool.pojo.dto.ThreadPoolConfigDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

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

    public List<ThreadPoolConfigAppDto> queryAndBuildConfigAppDtoByAppIds(List<String> appIds) {
        List<ThreadPoolConfigAppEntity> configAppEntities = configAppRep.listByAppIds(appIds);

        if (CollectionUtils.isEmpty(configAppEntities)) {
            return List.of();
        }

        appIds = configAppEntities.stream().map(ThreadPoolConfigAppEntity::getAppId).toList();

        List<ThreadPoolConfigEntity> configEntities = cursorQueryByAppIds(appIds);

        if (CollectionUtils.isEmpty(configEntities)) {
            return List.of();
        }

        Map<String, List<ThreadPoolConfigEntity>> configsMap = configEntities.stream()
                .collect(Collectors.groupingBy(ThreadPoolConfigEntity::getAppId));


        return configAppEntities.stream().map(configApp ->
                configConverter.buildConfigAppDto(configApp, configsMap.get(configApp.getAppId()))).toList();
    }

    private List<ThreadPoolConfigEntity> cursorQueryByAppIds(List<String> appIds) {
        List<ThreadPoolConfigEntity> allConfigs = new ArrayList<>();
        long cursor = 0;
        int batchSize = 500;

        while (true) {
            List<ThreadPoolConfigEntity> configs = configRep.listByCursor(cursor, batchSize, appIds);
            if (CollectionUtils.isEmpty(configs)) {
                break;
            }
            allConfigs.addAll(configs);
            cursor = configs.getLast().getId();
        }

        return allConfigs;
    }

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

    public Optional<ThreadPoolConfigAppDto> getConfigAppByAppId(String appId) {
        Optional<ThreadPoolConfigAppEntity> configAppEntityOptional = configAppRep.findByAppId(appId);
        return configAppEntityOptional.map(configApp -> {
            List<ThreadPoolConfigEntity> configs = configRep.findByAppId(appId);
            return configConverter.buildConfigAppDto(configApp, configs);
        });
    }

    public List<ThreadPoolConfigDto> listByAppId(String appId) {
        return configConverter.convertConfigDtos(configRep.findByAppId(appId));
    }

    public Optional<ThreadPoolConfigDto> getByAppIdAndPoolName(String appId, String poolName) {
        return configRep.findByAppIdAndPoolName(appId, poolName).map(configConverter::convertConfigDto);
    }

    /**
     * 返回全量配置
     *
     * @param cmd
     * @return
     */
    @Transactional(rollbackFor = Exception.class)
    public ThreadPoolConfigAppDto upsertConfigApp(ThreadPoolConfigAppUpsertCmd cmd) {
        ThreadPoolConfigAppEntity configApp = ensureConfigApp(cmd);

        List<ThreadPoolConfigUpsertCmd> configCmds = cmd.getConfigs();
        String appId = cmd.getAppId();
        List<ThreadPoolConfigEntity> totalConfigs = configRep.findByAppId(appId);
        Map<String, ThreadPoolConfigEntity> configMap = totalConfigs.stream().collect(Collectors.toMap(
                ThreadPoolConfigEntity::getPoolName, Function.identity()));

        List<ThreadPoolConfigEntity> newConfigs = new ArrayList<>();
        List<ThreadPoolConfigEntity> updatedConfigs = new ArrayList<>();
        List<ThreadPoolConfigEntity> configs = new ArrayList<>();

        for (ThreadPoolConfigUpsertCmd configCmd : configCmds) {
            ThreadPoolConfigEntity oldConfig = configMap.get(configCmd.getPoolName());
            if (oldConfig != null) {
                configConverter.cmdUpdateEntity(oldConfig, configCmd);
                updatedConfigs.add(oldConfig);
                configs.add(oldConfig);
            } else {
                ThreadPoolConfigEntity newConfig = configConverter.upsertCmdConvertEntity(configCmd, appId);
                newConfigs.add(newConfig);
                configs.add(newConfig);
                totalConfigs.add(newConfig);
            }
        }

        configRep.saveOrUpdateBatch(configs, 100);

        // 记录日志
        log(updatedConfigs, OperateType.UPDATE);

        log(newConfigs, OperateType.CREATE);

        Map<String, ThreadPoolConfigDto> dtoMap = configConverter.convertConfigDtos(totalConfigs).stream().collect(
                Collectors.toMap(ThreadPoolConfigDto::getPoolName, Function.identity()));

        return ThreadPoolConfigAppDto.builder().appId(appId).version(configApp.getVersion()).configs(dtoMap).build();
    }

    private void log(List<ThreadPoolConfigEntity> configs, OperateType operateType) {
        String operator = currentOperator();
        for (ThreadPoolConfigEntity config : configs) {
            logService.log(operateType, operator, config, String.valueOf(config.getId()), BizType.THREADPOOL_CONFIG);
        }
    }

    private ThreadPoolConfigAppEntity ensureConfigApp(ThreadPoolConfigAppUpsertCmd cmd) {
        Optional<ThreadPoolConfigAppEntity> configAppOptional = configAppRep.findByAppId(cmd.getAppId());
        ThreadPoolConfigAppEntity configApp = configAppOptional.orElseGet(() ->
                ThreadPoolConfigAppEntity.builder().appId(cmd.getAppId()).version(0L).build());

        configApp.setVersion(configApp.getVersion() + 1);
        configAppRep.saveOrUpdate(configApp);
        return configApp;
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteByAppId(String appId) {
        List<ThreadPoolConfigEntity> oldConfigs = configRep.findByAppId(appId);
        configAppRep.remove(Wrappers.<ThreadPoolConfigAppEntity>lambdaQuery().eq(ThreadPoolConfigAppEntity::getAppId, appId));
        configRep.deleteByAppId(appId);

        // 记录日志
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

    public List<ThreadPoolConfigAppRefreshPreCheckDto> refreshAllPreCheck() {
        List<ThreadPoolConfigAppEntity> configApps = configAppRep.list();
        Map<String, Long> appId2CfgCntMap = configRep.countAllByAppId();
        return configApps.stream().map(configApp -> buildRefreshPreCheckDto(configApp,
                appId2CfgCntMap)).toList();
    }

    public List<ThreadPoolConfigAppRefreshPreCheckDto> refreshPreCheckByAppIds(List<String> appIds) {
        List<ThreadPoolConfigAppEntity> configApps = configAppRep.list(Wrappers.<ThreadPoolConfigAppEntity>lambdaQuery()
                .in(ThreadPoolConfigAppEntity::getAppId, appIds));
        Map<String, Long> appId2CfgCntMap = configRep.countByAppIds(appIds);
        return configApps.stream().map(configApp -> buildRefreshPreCheckDto(configApp,
                appId2CfgCntMap)).toList();
    }

    private ThreadPoolConfigAppRefreshPreCheckDto buildRefreshPreCheckDto(ThreadPoolConfigAppEntity configApp,
                                                                          Map<String, Long> appId2CfgCntMap) {
        long version = configApp.getVersion();
        int configsCount = appId2CfgCntMap.getOrDefault(configApp.getAppId(), 0L).intValue();
        return ThreadPoolConfigAppRefreshPreCheckDto.builder()
                .appId(configApp.getAppId())
                .version(version)
                .configsCount(configsCount)
                .build();
    }

    private String currentOperator() {
        var ctx = AdminUserContextHolder.get();
        return ctx != null ? ctx.getUsername() : "system";
    }

    /**
     * 批量新增配置，返回列表中新增的和数据库已存在的配置
     *
     * @param cmd
     * @return
     */
    @Transactional(rollbackFor = Exception.class)
    public AddConfigAppResultDto addConfigApp(ThreadPoolConfigAppUpsertCmd cmd) {
        ThreadPoolConfigAppEntity configApp = ensureConfigApp(cmd);
        List<ThreadPoolConfigUpsertCmd> configCmds = cmd.getConfigs();
        List<String> poolNames = configCmds.stream().map(ThreadPoolConfigUpsertCmd::getPoolName).toList();

        String appId = cmd.getAppId();
        List<ThreadPoolConfigEntity> oldConfigs = configRep.findByAppIdAndPoolNamesIn(appId, poolNames);
        Map<String, ThreadPoolConfigEntity> configMap = oldConfigs.stream().collect(Collectors.toMap(
                ThreadPoolConfigEntity::getPoolName, Function.identity()));

        List<ThreadPoolConfigEntity> newConfigs = configCmds.stream().filter(configCmd ->
                !configMap.containsKey(configCmd.getPoolName())).map(configCmd ->
                configConverter.upsertCmdConvertEntity(configCmd, appId)).toList();

        boolean saved = configRep.saveBatch(newConfigs, 100);

        if (saved) {
            log(newConfigs, OperateType.CREATE);
        }

        return AddConfigAppResultDto.builder().appId(appId).version(configApp.getVersion())
                .existConfigs(configConverter.convertConfigDtos(oldConfigs))
                .addedConfigs(saved ? configConverter.convertConfigDtos(newConfigs) : List.of())
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
