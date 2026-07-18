package com.lezai.threadpool.service;

import com.lezai.threadpool.TestDataFactory;
import com.lezai.threadpool.bean.ConfigChangeNotification;
import com.lezai.threadpool.pojo.bean.ThreadPoolAppConfig;
import com.lezai.threadpool.exception.ResourceNotFoundException;
import com.lezai.threadpool.storage.ConfigStorage;
import com.lezai.threadpool.storage.listener.ConfigChangeListener;
import com.lezai.threadpool.storage.listener.ConfigChangeListenerManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock
    private ConfigStorage configStorage;

    @Mock
    private ScheduledExecutorService subscriptionExecutor;

    @Mock
    private ConfigChangeListenerManager listenerManager;

    @Captor
    private ArgumentCaptor<ConfigChangeListener> listenerCaptor;

    @Captor
    private ArgumentCaptor<Runnable> runnableCaptor;

    private SubscriptionService service;

    @BeforeEach
    void setUp() {
        service = new SubscriptionService(configStorage, subscriptionExecutor, listenerManager);
    }

    @Test
    @DisplayName("subscribe returns CompletableFuture and registers listener when version matches current")
    void subscribe_returnsCompletableFuture() {
        ThreadPoolAppConfig appConfig = ThreadPoolAppConfig.builder()
                .appId("app1").configVersion(5L)
                .configs(TestDataFactory.buildConfigList("pool-a")).build();
        when(configStorage.getAppConfig("app1")).thenReturn(Optional.of(appConfig));

        CompletableFuture<ConfigChangeNotification> future = service.subscribe("app1", 5L, 30000L);

        assertThat(future).isNotNull();
        assertThat(future.isDone()).isFalse();
        verify(listenerManager).register(eq("app1"), any(ConfigChangeListener.class));
        verify(subscriptionExecutor).schedule(any(Runnable.class), eq(1000L), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    @DisplayName("subscribe returns immediately with notification when client version is behind")
    void subscribe_immediateReturn_whenNewerVersionExists() {
        ThreadPoolAppConfig appConfig = ThreadPoolAppConfig.builder()
                .appId("app1").configVersion(10L)
                .configs(TestDataFactory.buildConfigList("pool-a")).build();
        when(configStorage.getAppConfig("app1")).thenReturn(Optional.of(appConfig));

        CompletableFuture<ConfigChangeNotification> future = service.subscribe("app1", 5L, 30000L);

        assertThat(future.isDone()).isTrue();
        ConfigChangeNotification notification = future.join();
        assertThat(notification.getAppId()).isEqualTo("app1");
        assertThat(notification.getVersion()).isEqualTo(10L);

        verify(listenerManager, never()).register(anyString(), any());
        verify(subscriptionExecutor, never()).schedule(any(Runnable.class), anyLong(), any());
    }

    @Test
    @DisplayName("subscribe throws ConfigNotFoundException when app not found")
    void subscribe_appNotFound() {
        when(configStorage.getAppConfig("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.subscribe("unknown", 1L, 30000L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("unknown");
    }

    @Test
    @DisplayName("subscribe registers listener when version matches")
    void subscribe_registersListenerWhenVersionMatches() {
        ThreadPoolAppConfig appConfig = ThreadPoolAppConfig.builder()
                .appId("app1").configVersion(5L)
                .configs(TestDataFactory.buildConfigList("pool-a")).build();
        when(configStorage.getAppConfig("app1")).thenReturn(Optional.of(appConfig));

        CompletableFuture<ConfigChangeNotification> future = service.subscribe("app1", 5L, 30000L);

        assertThat(future.isDone()).isFalse();
        verify(listenerManager).register(eq("app1"), listenerCaptor.capture());
        assertThat(listenerCaptor.getValue()).isNotNull();
    }

    @Test
    @DisplayName("subscribe schedules compensation poll with min(1000, timeout)")
    void subscribe_schedulesCompensationPoll() {
        ThreadPoolAppConfig appConfig = ThreadPoolAppConfig.builder()
                .appId("app1").configVersion(5L)
                .configs(TestDataFactory.buildConfigList("pool-a")).build();
        when(configStorage.getAppConfig("app1")).thenReturn(Optional.of(appConfig));

        service.subscribe("app1", 5L, 15000L);

        verify(subscriptionExecutor).schedule(any(Runnable.class), eq(1000L), eq(TimeUnit.MILLISECONDS));
    }
}
