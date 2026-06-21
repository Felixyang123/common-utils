package com.lezai.threadpool.storage.remote;

import com.lezai.threadpool.TestDataFactory;
import com.lezai.threadpool.bean.ThreadPoolAppConfig;
import com.lezai.threadpool.bean.ThreadPoolConfig;
import com.lezai.threadpool.converter.ThreadPoolConfigConverter;
import com.lezai.threadpool.pojo.cmd.ThreadPoolConfigUpsertCmd;
import com.lezai.threadpool.pojo.dto.ThreadPoolConfigAppDto;
import com.lezai.threadpool.pojo.dto.ThreadPoolConfigAppRefreshPreCheckDto;
import com.lezai.threadpool.pojo.dto.ThreadPoolConfigDto;
import com.lezai.threadpool.service.ThreadPoolConfigPersistenceService;
import com.lezai.threadpool.storage.listener.ConfigChangeListener;
import com.lezai.threadpool.storage.listener.ConfigChangeListenerManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisMysqlConfigStorageTest {

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private ThreadPoolConfigPersistenceService configService;

    @Mock
    private ThreadPoolConfigConverter configConverter;

    @Mock
    private ConfigChangeListenerManager listenerManager;

    private RedisMysqlConfigStorage storage;

    private final ConcurrentHashMap<String, ThreadPoolConfigAppDto> realMap = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() {
        RMap rMap = createRMapProxy(realMap);
        when(redissonClient.getMap(anyString())).thenReturn(rMap);
        storage = new RedisMysqlConfigStorage(redissonClient, configService, configConverter, listenerManager) {
            @Override
            public void loadAllFromDb() {
                // no-op: avoid concurrent mock access during constructor
            }
        };
    }

    @Test
    @DisplayName("getConfig returns config when cached")
    void getConfig_cached() {
        ThreadPoolConfigDto dto = new ThreadPoolConfigDto();
        dto.setPoolName("test-pool");
        dto.setCorePoolSize(4);
        ThreadPoolConfigAppDto appDto = ThreadPoolConfigAppDto.builder()
                .appId("app1").version(1L)
                .configs(Map.of("test-pool", dto))
                .build();
        ThreadPoolConfig expectedConfig = TestDataFactory.defaultThreadPoolConfig().build();

        realMap.put("app1", appDto);
        when(configConverter.dtoConvertConfig(dto)).thenReturn(expectedConfig);

        Optional<ThreadPoolConfig> result = storage.getConfig("app1", "test-pool");

        assertThat(result).isPresent();
        assertThat(result.get().getPoolName()).isEqualTo("test-pool");
        assertThat(result.get().getCorePoolSize()).isEqualTo(4);
    }

    @Test
    @DisplayName("getConfig loads from DB when not cached")
    void getConfig_loadFromDb() {
        ThreadPoolConfigDto dto = new ThreadPoolConfigDto();
        dto.setPoolName("test-pool");
        ThreadPoolConfigAppDto appDto = ThreadPoolConfigAppDto.builder()
                .appId("app1").version(1L)
                .configs(Map.of("test-pool", dto))
                .build();
        ThreadPoolConfig expectedConfig = TestDataFactory.defaultThreadPoolConfig().build();

        when(configService.getConfigAppByAppId("app1")).thenReturn(Optional.of(appDto));
        when(configConverter.dtoConvertConfig(dto)).thenReturn(expectedConfig);

        Optional<ThreadPoolConfig> result = storage.getConfig("app1", "test-pool");

        assertThat(result).isPresent();
        assertThat(result.get().getPoolName()).isEqualTo("test-pool");
    }

    @Test
    @DisplayName("getConfig returns empty when app not found")
    void getConfig_appNotFound() {
        when(configService.getConfigAppByAppId("app1")).thenReturn(Optional.empty());

        Optional<ThreadPoolConfig> result = storage.getConfig("app1", "test-pool");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getAppConfig returns app config")
    void getAppConfig() {
        ThreadPoolConfigAppDto appDto = ThreadPoolConfigAppDto.builder()
                .appId("app1").version(3L)
                .configs(Map.of("pool-a", new ThreadPoolConfigDto()))
                .build();
        ThreadPoolAppConfig expectedAppConfig = ThreadPoolAppConfig.builder()
                .appId("app1").configVersion(3L).configs(List.of())
                .build();

        realMap.put("app1", appDto);
        when(configService.refreshPreCheckByAppIds(List.of("app1"))).thenReturn(List.of());
        when(configConverter.dtoConvertAppConfig(appDto)).thenReturn(expectedAppConfig);

        Optional<ThreadPoolAppConfig> result = storage.getAppConfig("app1");

        assertThat(result).isPresent();
        assertThat(result.get().getAppId()).isEqualTo("app1");
        assertThat(result.get().getConfigVersion()).isEqualTo(3);
    }

    @Test
    @DisplayName("getAppConfig returns empty when not in cache")
    void getAppConfig_notFound() {
        when(configService.refreshPreCheckByAppIds(List.of("app1"))).thenReturn(
                List.of(ThreadPoolConfigAppRefreshPreCheckDto.builder().appId("app1").version(0L).configsCount(0).build()));
        when(configService.getConfigAppByAppId("app1")).thenReturn(Optional.empty());

        Optional<ThreadPoolAppConfig> result = storage.getAppConfig("app1");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getConfigs returns cached configs list")
    void getConfigs() {
        ThreadPoolConfigAppDto appDto = ThreadPoolConfigAppDto.builder()
                .appId("app1").version(1L)
                .configs(Map.of("pool-a", new ThreadPoolConfigDto()))
                .build();
        realMap.put("app1", appDto);
        when(configService.refreshPreCheckByAppIds(List.of("app1"))).thenReturn(List.of());
        when(configConverter.dtoConvertAppConfig(appDto)).thenReturn(
                ThreadPoolAppConfig.builder().appId("app1").configVersion(1L)
                        .configs(List.of(TestDataFactory.defaultThreadPoolConfig().build())).build());

        List<ThreadPoolConfig> result = storage.getConfigs("app1");

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("getConfigs returns empty when app not found")
    void getConfigs_notFound() {
        when(configService.refreshPreCheckByAppIds(List.of("app1"))).thenReturn(
                List.of(ThreadPoolConfigAppRefreshPreCheckDto.builder().appId("app1").version(0L).configsCount(0).build()));
        when(configService.getConfigAppByAppId("app1")).thenReturn(Optional.empty());

        assertThat(storage.getConfigs("app1")).isEmpty();
    }

    @Test
    @DisplayName("getConfigVersion returns version")
    void getConfigVersion() {
        ThreadPoolConfigAppDto appDto = ThreadPoolConfigAppDto.builder()
                .appId("app1").version(5L)
                .configs(Map.of())
                .build();
        realMap.put("app1", appDto);
        when(configService.refreshPreCheckByAppIds(List.of("app1"))).thenReturn(List.of());
        when(configConverter.dtoConvertAppConfig(appDto)).thenReturn(
                ThreadPoolAppConfig.builder().appId("app1").configVersion(5L).configs(List.of()).build());

        assertThat(storage.getConfigVersion("app1")).isEqualTo(5);
    }

    @Test
    @DisplayName("getConfigVersion returns 0 when not found")
    void getConfigVersion_notFound() {
        when(configService.refreshPreCheckByAppIds(List.of("unknown"))).thenReturn(
                List.of(ThreadPoolConfigAppRefreshPreCheckDto.builder().appId("unknown").version(0L).configsCount(0).build()));
        when(configService.getConfigAppByAppId("unknown")).thenReturn(Optional.empty());

        assertThat(storage.getConfigVersion("unknown")).isZero();
    }

    @Test
    @DisplayName("saveConfig saves to service and updates cache")
    void saveConfig() {
        ThreadPoolConfig config = TestDataFactory.defaultThreadPoolConfig().build();
        ThreadPoolConfigUpsertCmd upsertCmd = new ThreadPoolConfigUpsertCmd();
        ThreadPoolConfigAppDto resultDto = ThreadPoolConfigAppDto.builder()
                .appId("app1").version(2L)
                .configs(Map.of("test-pool", new ThreadPoolConfigDto()))
                .build();

        when(configConverter.configConvertUpsertCmd(config)).thenReturn(upsertCmd);
        when(configService.upsertConfigApp(any())).thenReturn(resultDto);

        storage.saveConfig("app1", config);

        verify(configService).upsertConfigApp(any());
        assertThat(realMap).containsKey("app1");
    }

    @Test
    @DisplayName("saveConfigs saves multiple configs")
    void saveConfigs() {
        ThreadPoolConfig config1 = TestDataFactory.defaultThreadPoolConfig().poolName("pool-a").build();
        ThreadPoolConfig config2 = TestDataFactory.defaultThreadPoolConfig().poolName("pool-b").build();
        ThreadPoolConfigUpsertCmd cmd = new ThreadPoolConfigUpsertCmd();
        ThreadPoolConfigAppDto resultDto = ThreadPoolConfigAppDto.builder()
                .appId("app1").version(1L)
                .configs(new ConcurrentHashMap<>(Map.of("pool-a", new ThreadPoolConfigDto(), "pool-b", new ThreadPoolConfigDto())))
                .build();

        when(configConverter.configConvertUpsertCmd(any())).thenReturn(cmd);
        when(configService.upsertConfigApp(any())).thenReturn(resultDto);

        storage.saveConfigs("app1", List.of(config1, config2));

        verify(configService, times(2)).upsertConfigApp(any());
    }

    @Test
    @DisplayName("deleteConfig removes from cache and service")
    void deleteConfig() {
        ThreadPoolConfigDto dto = new ThreadPoolConfigDto();
        dto.setPoolName("pool-a");
        ThreadPoolConfigAppDto appDto = ThreadPoolConfigAppDto.builder()
                .appId("app1").version(2L)
                .configs(new ConcurrentHashMap<>(Map.of("pool-a", dto, "pool-b", new ThreadPoolConfigDto())))
                .build();
        realMap.put("app1", appDto);
        when(configService.deleteByAppIdAndPoolName("app1", "pool-a"))
                .thenReturn(Optional.of(ThreadPoolConfigAppDto.builder().appId("app1").build()));

        storage.deleteConfig("app1", "pool-a");

        verify(configService).deleteByAppIdAndPoolName("app1", "pool-a");
    }

    @Test
    @DisplayName("deleteConfigs removes all configs")
    void deleteConfigs() {
        ThreadPoolConfigAppDto appDto = ThreadPoolConfigAppDto.builder()
                .appId("app1").version(1L).configs(Map.of()).build();
        realMap.put("app1", appDto);

        storage.deleteConfigs("app1");

        verify(configService).deleteByAppId("app1");
        assertThat(realMap).doesNotContainKey("app1");
    }

    @Test
    @DisplayName("addConfig adds new config and returns it")
    void addConfig() {
        ThreadPoolConfig config = TestDataFactory.defaultThreadPoolConfig().build();
        ThreadPoolConfigUpsertCmd upsertCmd = new ThreadPoolConfigUpsertCmd();
        ThreadPoolConfigDto configDto = new ThreadPoolConfigDto();
        configDto.setPoolName("test-pool");
        ThreadPoolConfigAppDto resultDto = ThreadPoolConfigAppDto.builder()
                .appId("app1").version(1L)
                .configs(Map.of("test-pool", configDto))
                .build();
        ThreadPoolConfig returnedConfig = TestDataFactory.defaultThreadPoolConfig().build();

        when(configConverter.configConvertUpsertCmd(config)).thenReturn(upsertCmd);
        when(configService.addConfigApp(any(), anyList())).thenReturn(resultDto);
        when(configConverter.dtoConvertConfig(configDto)).thenReturn(returnedConfig);

        ThreadPoolConfig result = storage.addConfig("app1", config);

        assertThat(result).isNotNull();
        assertThat(result.getPoolName()).isEqualTo("test-pool");
    }

    @Test
    @DisplayName("addConfigs adds multiple configs")
    void addConfigs() {
        ThreadPoolConfig config = TestDataFactory.defaultThreadPoolConfig().build();
        ThreadPoolConfigUpsertCmd upsertCmd = new ThreadPoolConfigUpsertCmd();
        ThreadPoolConfigDto configDto = new ThreadPoolConfigDto();
        configDto.setPoolName("test-pool");
        ThreadPoolConfigAppDto resultDto = ThreadPoolConfigAppDto.builder()
                .appId("app1").version(1L)
                .configs(Map.of("test-pool", configDto))
                .build();
        ThreadPoolConfig returnedConfig = TestDataFactory.defaultThreadPoolConfig().build();

        when(configConverter.configConvertUpsertCmdBatch(List.of(config))).thenReturn(List.of(upsertCmd));
        when(configService.addConfigApp(any(), anyList())).thenReturn(resultDto);
        when(configConverter.dtoConvertConfig(configDto)).thenReturn(returnedConfig);

        List<ThreadPoolConfig> result = storage.addConfigs("app1", List.of(config));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPoolName()).isEqualTo("test-pool");
    }

    @Test
    @DisplayName("registerChangeListener delegates to listenerManager")
    void registerChangeListener() {
        ConfigChangeListener listener = (appId, version) -> {};
        storage.registerChangeListener("app1", listener);

        verify(listenerManager).register("app1", listener);
    }

    @Test
    @DisplayName("getAllConfigs returns all configs from cache")
    void getAllConfigs() {
        ThreadPoolConfigDto dto1 = new ThreadPoolConfigDto();
        dto1.setPoolName("pool-a");
        ThreadPoolConfigDto dto2 = new ThreadPoolConfigDto();
        dto2.setPoolName("pool-b");
        realMap.put("app1", ThreadPoolConfigAppDto.builder()
                .appId("app1").version(1L).configs(Map.of("pool-a", dto1)).build());
        realMap.put("app2", ThreadPoolConfigAppDto.builder()
                .appId("app2").version(1L).configs(Map.of("pool-b", dto2)).build());

        when(configService.refreshAllPreCheck()).thenReturn(List.of(
                ThreadPoolConfigAppRefreshPreCheckDto.builder().appId("app1").version(1L).configsCount(1).build(),
                ThreadPoolConfigAppRefreshPreCheckDto.builder().appId("app2").version(1L).configsCount(1).build()));
        when(configConverter.dtoConvertConfigBatch(anyCollection())).thenReturn(
                List.of(TestDataFactory.defaultThreadPoolConfig().poolName("pool-a").build()),
                List.of(TestDataFactory.defaultThreadPoolConfig().poolName("pool-b").build()));

        Map<String, List<ThreadPoolConfig>> all = storage.getAllConfigs();

        assertThat(all).containsKeys("app1", "app2");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static <T> RMap createRMapProxy(ConcurrentMap<String, T> backingMap) {
        return (RMap) Proxy.newProxyInstance(
                RMap.class.getClassLoader(),
                new Class<?>[]{RMap.class},
                (proxy, method, args) -> {
                    try {
                        Method targetMethod = ConcurrentHashMap.class.getMethod(
                                method.getName(), method.getParameterTypes());
                        try {
                            return targetMethod.invoke(backingMap, args);
                        } catch (InvocationTargetException e) {
                            throw e.getCause();
                        }
                    } catch (NoSuchMethodException e) {
                        return null;
                    }
                }
        );
    }
}
