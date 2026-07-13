package com.lezai.threadpool.service;

import com.lezai.threadpool.bean.ThreadPoolConfig;
import com.lezai.threadpool.converter.ThreadPoolConfigConverter;
import com.lezai.threadpool.dao.entity.ThreadPoolConfigAppEntity;
import com.lezai.threadpool.dao.entity.ThreadPoolConfigEntity;
import com.lezai.threadpool.dao.rep.ThreadPoolConfigAppRep;
import com.lezai.threadpool.dao.rep.ThreadPoolConfigRep;
import com.lezai.threadpool.pojo.bean.ThreadPoolConfigApp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
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
        ThreadPoolConfigApp dto = ThreadPoolConfigApp.builder().appId("app1").build();

        when(configAppRep.findByAppId("app1")).thenReturn(Optional.of(appEntity));
        when(configRep.findByAppId("app1")).thenReturn(configEntities);
        when(configConverter.buildConfigApp(appEntity, configEntities)).thenReturn(dto);

        Optional<ThreadPoolConfigApp> result = service.getConfigAppByAppId("app1");

        assertThat(result).isPresent();
        assertThat(result.get().getAppId()).isEqualTo("app1");
    }

    @Test
    @DisplayName("getConfigAppByAppId returns empty when not found")
    void getConfigAppByAppId_notFound() {
        when(configAppRep.findByAppId("unknown")).thenReturn(Optional.empty());

        Optional<ThreadPoolConfigApp> result = service.getConfigAppByAppId("unknown");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("listByAppId returns config DTOs")
    void listByAppId() {
        List<ThreadPoolConfigEntity> entities = List.of(ThreadPoolConfigEntity.builder().build());
        List<ThreadPoolConfig> dtos = List.of(createConfig("pool-a"));

        when(configRep.findByAppId("app1")).thenReturn(entities);
        when(configConverter.convertConfigs(entities)).thenReturn(dtos);

        List<ThreadPoolConfig> result = service.listByAppId("app1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPoolName()).isEqualTo("pool-a");
    }

    @Test
    @DisplayName("getByAppIdAndPoolName returns config when found")
    void getByAppIdAndPoolName_found() {
        ThreadPoolConfigEntity entity = ThreadPoolConfigEntity.builder().build();
        ThreadPoolConfig config = createConfig("pool-a");

        when(configRep.findByAppIdAndPoolName("app1", "pool-a")).thenReturn(Optional.of(entity));
        when(configConverter.convertConfig(entity)).thenReturn(config);

        Optional<ThreadPoolConfig> result = service.getByAppIdAndPoolName("app1", "pool-a");

        assertThat(result).isPresent();
        assertThat(result.get().getPoolName()).isEqualTo("pool-a");
    }

    @Test
    @DisplayName("getByAppIdAndPoolName returns empty when not found")
    void getByAppIdAndPoolName_notFound() {
        when(configRep.findByAppIdAndPoolName("app1", "unknown")).thenReturn(Optional.empty());

        Optional<ThreadPoolConfig> result = service.getByAppIdAndPoolName("app1", "unknown");

        assertThat(result).isEmpty();
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
        when(configRep.removeById(configEntity)).thenReturn(true);
        when(configRep.countByAppId("app1")).thenReturn(1L);

        service.deleteByAppIdAndPoolName("app1", "pool-a");

        verify(configRep).removeById(configEntity);
        verify(configAppRep).saveOrUpdate(appEntity);
    }

    @Test
    @DisplayName("deleteByAppIdAndPoolName returns empty when app not found")
    void deleteByAppIdAndPoolName_appNotFound() {
        when(configAppRep.findByAppId("unknown")).thenReturn(Optional.empty());

        service.deleteByAppIdAndPoolName("unknown", "pool-a");
    }

    private static ThreadPoolConfig createConfig(String poolName) {
        return ThreadPoolConfig.builder().poolName(poolName).build();
    }
}


