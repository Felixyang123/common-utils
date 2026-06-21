package com.lezai.threadpool.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.lezai.threadpool.TestDataFactory;
import com.lezai.threadpool.bean.ThreadPoolConfig;
import com.lezai.threadpool.converter.ThreadPoolConfigConverter;
import com.lezai.threadpool.dao.entity.ThreadPoolConfigAppEntity;
import com.lezai.threadpool.dao.entity.ThreadPoolConfigEntity;
import com.lezai.threadpool.dao.rep.ThreadPoolConfigAppRep;
import com.lezai.threadpool.dao.rep.ThreadPoolConfigRep;
import com.lezai.threadpool.pojo.cmd.ThreadPoolConfigAppUpsertCmd;
import com.lezai.threadpool.pojo.cmd.ThreadPoolConfigUpsertCmd;
import com.lezai.threadpool.pojo.dto.ThreadPoolConfigAppDto;
import com.lezai.threadpool.pojo.dto.ThreadPoolConfigAppRefreshPreCheckDto;
import com.lezai.threadpool.pojo.dto.ThreadPoolConfigDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ThreadPoolConfigPersistenceServiceTest {

    @Mock
    private ThreadPoolConfigRep configRep;

    @Mock
    private ThreadPoolConfigAppRep configAppRep;

    @Mock
    private ThreadPoolConfigConverter configConverter;

    @Mock
    private OperateLogService logService;

    private ThreadPoolConfigPersistenceService service;

    @BeforeEach
    void setUp() {
        service = new ThreadPoolConfigPersistenceService(configRep, configAppRep, configConverter, logService);
    }

    @Test
    @DisplayName("allAppIds returns all app IDs")
    void allAppIds() {
        when(configAppRep.allAppIds()).thenReturn(List.of("app1", "app2"));

        List<String> result = service.allAppIds();

        assertThat(result).containsExactly("app1", "app2");
    }

    @Test
    @DisplayName("getConfigAppByAppId returns config when found")
    void getConfigAppByAppId_found() {
        ThreadPoolConfigAppEntity appEntity = ThreadPoolConfigAppEntity.builder()
                .appId("app1").version(3L).build();
        List<ThreadPoolConfigEntity> configEntities = List.of(ThreadPoolConfigEntity.builder().build());
        ThreadPoolConfigAppDto dto = ThreadPoolConfigAppDto.builder().appId("app1").build();

        when(configAppRep.findByAppId("app1")).thenReturn(Optional.of(appEntity));
        when(configRep.findByAppId("app1")).thenReturn(configEntities);
        when(configConverter.buildConfigAppDto(appEntity, configEntities)).thenReturn(dto);

        Optional<ThreadPoolConfigAppDto> result = service.getConfigAppByAppId("app1");

        assertThat(result).isPresent();
        assertThat(result.get().getAppId()).isEqualTo("app1");
    }

    @Test
    @DisplayName("getConfigAppByAppId returns empty when not found")
    void getConfigAppByAppId_notFound() {
        when(configAppRep.findByAppId("unknown")).thenReturn(Optional.empty());

        Optional<ThreadPoolConfigAppDto> result = service.getConfigAppByAppId("unknown");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("listByAppId returns config DTOs")
    void listByAppId() {
        List<ThreadPoolConfigEntity> entities = List.of(ThreadPoolConfigEntity.builder().build());
        List<ThreadPoolConfigDto> dtos = List.of(createConfigDto("pool-a"));

        when(configRep.findByAppId("app1")).thenReturn(entities);
        when(configConverter.convertConfigDtos(entities)).thenReturn(dtos);

        List<ThreadPoolConfigDto> result = service.listByAppId("app1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPoolName()).isEqualTo("pool-a");
    }

    @Test
    @DisplayName("getByAppIdAndPoolName returns config when found")
    void getByAppIdAndPoolName_found() {
        ThreadPoolConfigEntity entity = ThreadPoolConfigEntity.builder().build();
        ThreadPoolConfigDto dto = createConfigDto("pool-a");

        when(configRep.findByAppIdAndPoolName("app1", "pool-a")).thenReturn(Optional.of(entity));
        when(configConverter.convertConfigDto(entity)).thenReturn(dto);

        Optional<ThreadPoolConfigDto> result = service.getByAppIdAndPoolName("app1", "pool-a");

        assertThat(result).isPresent();
        assertThat(result.get().getPoolName()).isEqualTo("pool-a");
    }

    @Test
    @DisplayName("getByAppIdAndPoolName returns empty when not found")
    void getByAppIdAndPoolName_notFound() {
        when(configRep.findByAppIdAndPoolName("app1", "unknown")).thenReturn(Optional.empty());

        Optional<ThreadPoolConfigDto> result = service.getByAppIdAndPoolName("app1", "unknown");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("upsertConfigApp creates new app and upserts configs")
    void upsertConfigApp_create() {
        ThreadPoolConfigUpsertCmd configCmd = new ThreadPoolConfigUpsertCmd();
        configCmd.setPoolName("pool-a");
        ThreadPoolConfigAppUpsertCmd cmd = ThreadPoolConfigAppUpsertCmd.builder()
                .appId("app1")
                .configs(List.of(configCmd))
                .build();

        ThreadPoolConfigAppEntity newAppEntity = ThreadPoolConfigAppEntity.builder()
                .appId("app1").version(0L).build();
        ThreadPoolConfigEntity newConfigEntity = ThreadPoolConfigEntity.builder().poolName("pool-a").build();

        when(configAppRep.findByAppId("app1")).thenReturn(Optional.empty());
        when(configConverter.upsertCmdConvertEntity(cmd)).thenReturn(newAppEntity);
        when(configRep.findByAppIdAndPoolNamesIn("app1", List.of("pool-a"))).thenReturn(List.of());
        when(configConverter.upsertCmdConvertEntity(configCmd)).thenReturn(newConfigEntity);
        when(configConverter.convertConfigDtos(anyList())).thenReturn(List.of(createConfigDto("pool-a")));

        ThreadPoolConfigAppDto result = service.upsertConfigApp(cmd);

        assertThat(result.getAppId()).isEqualTo("app1");
        verify(configRep).saveOrUpdateBatch(anyList(), eq(100));
    }

    @Test
    @DisplayName("deleteByAppId removes all configs for app and logs")
    void deleteByAppId() {
        ThreadPoolConfigEntity entity = ThreadPoolConfigEntity.builder().poolName("pool-a").build();
        when(configRep.findByAppId("app1")).thenReturn(List.of(entity));

        service.deleteByAppId("app1");

        verify(configAppRep).remove(any());
        verify(configRep).deleteByAppId("app1");
        verify(logService).log(any(), any(), eq(entity), any(), any());
    }

    @Test
    @DisplayName("deleteByAppIdAndPoolName deletes single pool")
    void deleteByAppIdAndPoolName() {
        ThreadPoolConfigAppEntity appEntity = ThreadPoolConfigAppEntity.builder()
                .appId("app1").version(2L).build();
        ThreadPoolConfigEntity configEntity = ThreadPoolConfigEntity.builder().id(1L).build();

        when(configAppRep.findByAppId("app1")).thenReturn(Optional.of(appEntity));
        when(configRep.findByAppIdAndPoolName("app1", "pool-a")).thenReturn(Optional.of(configEntity));
        when(configRep.countByAppId("app1")).thenReturn(1L);
        when(configConverter.convertDto(appEntity))
                .thenReturn(ThreadPoolConfigAppDto.builder().appId("app1").build());

        Optional<ThreadPoolConfigAppDto> result = service.deleteByAppIdAndPoolName("app1", "pool-a");

        assertThat(result).isPresent();
        assertThat(result.get().getAppId()).isEqualTo("app1");
        verify(configRep).removeById(1L);
    }

    @Test
    @DisplayName("deleteByAppIdAndPoolName returns empty when app not found")
    void deleteByAppIdAndPoolName_appNotFound() {
        when(configAppRep.findByAppId("unknown")).thenReturn(Optional.empty());

        Optional<ThreadPoolConfigAppDto> result = service.deleteByAppIdAndPoolName("unknown", "pool-a");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("queryAndBuildConfigAppDtoByAppIds returns configs")
    void queryAndBuildConfigAppDtoByAppIds() {
        ThreadPoolConfigAppEntity appEntity = ThreadPoolConfigAppEntity.builder()
                .appId("app1").build();
        ThreadPoolConfigEntity configEntity = ThreadPoolConfigEntity.builder()
                .appId("app1").id(1L).build();

        when(configAppRep.listByAppIds(List.of("app1"))).thenReturn(List.of(appEntity));
        when(configRep.listByCursor(0, 500, List.of("app1"))).thenReturn(List.of(configEntity));
        when(configConverter.buildConfigAppDto(appEntity, List.of(configEntity)))
                .thenReturn(ThreadPoolConfigAppDto.builder().appId("app1").build());

        List<ThreadPoolConfigAppDto> result = service.queryAndBuildConfigAppDtoByAppIds(List.of("app1"));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAppId()).isEqualTo("app1");
    }

    @Test
    @DisplayName("queryAndBuildConfigAppDtoByAppIds returns empty when no config apps found")
    void queryAndBuildConfigAppDtoByAppIds_noApps() {
        when(configAppRep.listByAppIds(List.of("unknown"))).thenReturn(List.of());

        List<ThreadPoolConfigAppDto> result = service.queryAndBuildConfigAppDtoByAppIds(List.of("unknown"));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("refreshAllPreCheck returns pre-check DTOs")
    void refreshAllPreCheck() {
        ThreadPoolConfigAppEntity appEntity = ThreadPoolConfigAppEntity.builder()
                .appId("app1").version(3L).build();

        when(configAppRep.list()).thenReturn(List.of(appEntity));
        when(configRep.countAllByAppId()).thenReturn(Map.of("app1", 5L));

        List<ThreadPoolConfigAppRefreshPreCheckDto> result = service.refreshAllPreCheck();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAppId()).isEqualTo("app1");
        assertThat(result.get(0).getVersion()).isEqualTo(3);
        assertThat(result.get(0).getConfigsCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("refreshPreCheckByAppIds returns pre-check DTOs for specific apps")
    void refreshPreCheckByAppIds() {
        ThreadPoolConfigAppEntity appEntity = ThreadPoolConfigAppEntity.builder()
                .appId("app1").version(3L).build();

        when(configAppRep.list(any(Wrapper.class))).thenReturn(List.of(appEntity));
        when(configRep.countByAppIds(List.of("app1"))).thenReturn(Map.of("app1", 5L));

        List<ThreadPoolConfigAppRefreshPreCheckDto> result = service.refreshPreCheckByAppIds(List.of("app1"));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAppId()).isEqualTo("app1");
        assertThat(result.get(0).getConfigsCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("addConfigApp adds configs and returns dto")
    void addConfigApp() {
        ThreadPoolConfigUpsertCmd configCmd = new ThreadPoolConfigUpsertCmd();
        configCmd.setPoolName("pool-a");
        ThreadPoolConfigAppUpsertCmd cmd = ThreadPoolConfigAppUpsertCmd.builder()
                .appId("app1")
                .configs(List.of(configCmd))
                .build();

        when(configRep.findByAppIdAndPoolNamesIn("app1", List.of("pool-a"))).thenReturn(List.of());
        when(configConverter.upsertCmdConvertEntity(configCmd))
                .thenReturn(ThreadPoolConfigEntity.builder().poolName("pool-a").build());
        when(configRep.saveBatch(anyList(), eq(100))).thenReturn(true);
        when(configAppRep.findByAppId("app1")).thenReturn(Optional.empty());
        when(configConverter.convertConfigDtos(anyList()))
                .thenReturn(List.of(createConfigDto("pool-a")));
        when(configConverter.convertConfigs(anyList()))
                .thenReturn(List.of(TestDataFactory.defaultThreadPoolConfig().build()));

        List<ThreadPoolConfig> addedConfigs = new java.util.ArrayList<>();
        ThreadPoolConfigAppDto result = service.addConfigApp(cmd, addedConfigs);

        assertThat(result.getAppId()).isEqualTo("app1");
        assertThat(addedConfigs).isNotEmpty();
    }

    private static ThreadPoolConfigDto createConfigDto(String poolName) {
        ThreadPoolConfigDto dto = new ThreadPoolConfigDto();
        dto.setPoolName(poolName);
        return dto;
    }
}
