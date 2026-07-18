package com.lezai.threadpool.service;

import com.lezai.threadpool.bean.ThreadPoolConfig;
import com.lezai.threadpool.converter.ThreadPoolConfigConverter;
import com.lezai.threadpool.dao.entity.ThreadPoolConfigAppEntity;
import com.lezai.threadpool.dao.entity.ThreadPoolConfigEntity;
import com.lezai.threadpool.dao.rep.ThreadPoolConfigAppRep;
import com.lezai.threadpool.audit.AuditEvent;
import com.lezai.threadpool.dao.rep.ThreadPoolConfigRep;
import com.lezai.threadpool.pojo.bean.ThreadPoolConfigApp;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
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
    private ApplicationEventPublisher eventPublisher;

    private ThreadPoolConfigPersistenceService service;

    @BeforeEach
    void setUp() {
        service = new ThreadPoolConfigPersistenceService(configRep, configAppRep, configConverter, eventPublisher);
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
        verify(eventPublisher).publishEvent(any(AuditEvent.class));
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

    @Test
    @DisplayName("addConfigApp: single query, split active/exist/retired in memory; tombstone NOT resurrected")
    void addConfigApp_threeState_singleQuery() {
        // app entry exists at version 1 -> ensureConfigApp bumps to 2
        ThreadPoolConfigAppEntity appEntity = ThreadPoolConfigAppEntity.builder()
                .appId("app1").version(1L).build();
        when(configAppRep.findByAppId("app1")).thenReturn(Optional.of(appEntity));

        // pool-a: active (deleted=false) -> existConfigs (no update)
        // pool-b: soft-deleted (deleted=true) -> retiredConfigs (NOT resurrected)
        // pool-c: absent -> addedConfigs
        ThreadPoolConfigEntity activeA = ThreadPoolConfigEntity.builder()
                .id(10L).appId("app1").poolName("pool-a").deleted(false).build();
        ThreadPoolConfigEntity deletedB = ThreadPoolConfigEntity.builder()
                .id(20L).appId("app1").poolName("pool-b").deleted(true).build();
        when(configRep.findAllByAppIdAndPoolNamesIn(eq("app1"), anyList()))
                .thenReturn(List.of(activeA, deletedB));

        ThreadPoolConfig configC = ThreadPoolConfig.builder().poolName("pool-c").build();
        ThreadPoolConfigEntity newEntityC = ThreadPoolConfigEntity.builder()
                .appId("app1").poolName("pool-c").build();
        when(configConverter.configConvertEntity(any(), eq("app1"))).thenReturn(newEntityC);
        when(configRep.saveBatch(anyList(), anyInt())).thenReturn(true);

        ThreadPoolConfig configA = ThreadPoolConfig.builder().poolName("pool-a").build();
        ThreadPoolConfig configB = ThreadPoolConfig.builder().poolName("pool-b").build();
        ThreadPoolConfig configCDto = ThreadPoolConfig.builder().poolName("pool-c").build();
        when(configConverter.convertConfigs(argThat(list -> list != null && !list.isEmpty() && "pool-a".equals(list.get(0).getPoolName()))))
                .thenReturn(List.of(configA));
        when(configConverter.convertConfigs(argThat(list -> list != null && !list.isEmpty() && "pool-b".equals(list.get(0).getPoolName()))))
                .thenReturn(List.of(configB));
        when(configConverter.convertConfigs(argThat(list -> list != null && !list.isEmpty() && "pool-c".equals(list.get(0).getPoolName()))))
                .thenReturn(List.of(configCDto));

        ThreadPoolConfigApp result = service.addConfigApp("app1",
                List.of(ThreadPoolConfig.builder().poolName("pool-a").build(),
                        ThreadPoolConfig.builder().poolName("pool-b").build(),
                        configC));

        // retired pool-b must NOT trigger a saveBatch resurrection (only new pool-c saved)
        verify(configRep).saveBatch(argThat((java.util.Collection<ThreadPoolConfigEntity> list) ->
                list.size() == 1 && "pool-c".equals(list.iterator().next().getPoolName())), eq(100));

        assertThat(result.getExistConfigs()).extracting(ThreadPoolConfig::getPoolName).containsExactly("pool-a");
        assertThat(result.getRetiredConfigs()).extracting(ThreadPoolConfig::getPoolName).containsExactly("pool-b");
        assertThat(result.getAddedConfigs()).extracting(ThreadPoolConfig::getPoolName).containsExactly("pool-c");
        assertThat(result.getVersion()).isEqualTo(2L);
        assertThat(result.getAppId()).isEqualTo("app1");
    }

    private static ThreadPoolConfig createConfig(String poolName) {
        return ThreadPoolConfig.builder().poolName(poolName).build();
    }
}


