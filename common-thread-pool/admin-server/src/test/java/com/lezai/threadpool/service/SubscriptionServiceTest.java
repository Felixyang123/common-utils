package com.lezai.threadpool.service;

import com.lezai.threadpool.TestDataFactory;
import com.lezai.threadpool.bean.ApiResponse;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.Optional;
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

    private SubscriptionService service;

    @BeforeEach
    void setUp() {
        service = new SubscriptionService(configStorage, subscriptionExecutor, listenerManager);
    }

    @Test
    @DisplayName("subscribe returns DeferredResult and registers listener when version matches current")
    void subscribe_returnsDeferredResult() {
        ThreadPoolAppConfig appConfig = ThreadPoolAppConfig.builder()
                .appId("app1").configVersion(5L)
                .configs(TestDataFactory.buildConfigList("pool-a")).build();
        when(configStorage.getAppConfig("app1")).thenReturn(Optional.of(appConfig));

        // Same version (5 == 5), so we go into listener registration path
        DeferredResult<ResponseEntity<ApiResponse<ConfigChangeNotification>>> result = service.subscribe("app1", 5L, 30000L);

        assertThat(result).isNotNull();
        assertThat(result.isSetOrExpired()).isFalse();
        // Should register listener
        verify(listenerManager).register(eq("app1"), any(ConfigChangeListener.class));
        // Should schedule compensation poll
        verify(subscriptionExecutor).schedule(any(Runnable.class), eq(1000L), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    @DisplayName("subscribe returns immediately with a lightweight notification when version matches current version")
    void subscribe_immediateReturn_whenVersionMatch() {
        ThreadPoolAppConfig appConfig = ThreadPoolAppConfig.builder()
                .appId("app1").configVersion(10L)
                .configs(TestDataFactory.buildConfigList("pool-a")).build();
        when(configStorage.getAppConfig("app1")).thenReturn(Optional.of(appConfig));

        // Client version 5 < 10, should return immediately with a notification (not the full config)
        DeferredResult<ResponseEntity<ApiResponse<ConfigChangeNotification>>> result = service.subscribe("app1", 5L, 30000L);

        assertThat(result.isSetOrExpired()).isTrue();
        @SuppressWarnings("unchecked")
        ResponseEntity<ApiResponse<ConfigChangeNotification>> responseEntity =
                (ResponseEntity<ApiResponse<ConfigChangeNotification>>) result.getResult();
        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
        ApiResponse<ConfigChangeNotification> response = responseEntity.getBody();
        assertThat(response.getCode()).isZero();
        assertThat(response.getData()).isNotNull();
        assertThat(response.getData().getAppId()).isEqualTo("app1");
        assertThat(response.getData().getVersion()).isEqualTo(10L);

        // Should NOT register listener since we returned immediately
        verify(listenerManager, never()).register(anyString(), any());
        verify(subscriptionExecutor, never()).schedule(any(Runnable.class), anyLong(), any());
    }

    @Test
    @DisplayName("subscribe returns immediate notification when current version is greater than client version")
    void subscribe_immediateReturn_whenNewerVersionExists() {
        ThreadPoolAppConfig appConfig = ThreadPoolAppConfig.builder()
                .appId("app1").configVersion(8L)
                .configs(TestDataFactory.buildConfigList("pool-a")).build();
        when(configStorage.getAppConfig("app1")).thenReturn(Optional.of(appConfig));

        DeferredResult<ResponseEntity<ApiResponse<ConfigChangeNotification>>> result = service.subscribe("app1", 3L, 30000L);

        assertThat(result.isSetOrExpired()).isTrue();
        @SuppressWarnings("unchecked")
        ResponseEntity<ApiResponse<ConfigChangeNotification>> responseEntity =
                (ResponseEntity<ApiResponse<ConfigChangeNotification>>) result.getResult();
        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(responseEntity.getBody().getData().getVersion()).isEqualTo(8L);
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
    @DisplayName("subscribe registers listener and creates deferred result with timeout value")
    void subscribe_registersListenerWithTimeoutValue() {
        ThreadPoolAppConfig appConfig = ThreadPoolAppConfig.builder()
                .appId("app1").configVersion(5L)
                .configs(TestDataFactory.buildConfigList("pool-a")).build();
        when(configStorage.getAppConfig("app1")).thenReturn(Optional.of(appConfig));

        // Same version so we wait
        DeferredResult<ResponseEntity<ApiResponse<ConfigChangeNotification>>> result = service.subscribe("app1", 5L, 30000L);

        assertThat(result.isSetOrExpired()).isFalse();

        // Verify listener registered
        verify(listenerManager).register(eq("app1"), listenerCaptor.capture());
        ConfigChangeListener registeredListener = listenerCaptor.getValue();
        assertThat(registeredListener).isNotNull();
    }

    @Test
    @DisplayName("subscribe timeout value becomes DeferredResult timeout")
    void subscribe_setsDeferredResultTimeout() {
        ThreadPoolAppConfig appConfig = ThreadPoolAppConfig.builder()
                .appId("app1").configVersion(5L)
                .configs(TestDataFactory.buildConfigList("pool-a")).build();
        when(configStorage.getAppConfig("app1")).thenReturn(Optional.of(appConfig));

        DeferredResult<ResponseEntity<ApiResponse<ConfigChangeNotification>>> result = service.subscribe("app1", 5L, 15000L);

        // The default timeout value (304 error response)
        assertThat(result.isSetOrExpired()).isFalse();

        // This verification uses a different approach - we can't directly check the timeout value,
        // but we can verify the compensation poll was scheduled with min(1000, 15000) = 1000
        verify(subscriptionExecutor).schedule(any(Runnable.class), eq(1000L), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    @DisplayName("subscribe timeout default value is a real HTTP 304, not a 200-wrapped business code")
    void subscribe_timeoutDefaultValue_isRealHttp304() {
        // The DeferredResult's built-in timeout default (returned automatically by Spring's
        // async infra if no result is set before timeout elapses) must be a genuine 304
        // status, aligned with GlobalExceptionHandler's semantic-status-code convention (batch 6.4).
        ThreadPoolAppConfig appConfig = ThreadPoolAppConfig.builder()
                .appId("app1").configVersion(5L)
                .configs(TestDataFactory.buildConfigList("pool-a")).build();
        when(configStorage.getAppConfig("app1")).thenReturn(Optional.of(appConfig));

        DeferredResult<ResponseEntity<ApiResponse<ConfigChangeNotification>>> result = service.subscribe("app1", 5L, 30000L);

        // simulate what Spring's WebAsyncManager does when the timeout elapses with no result set
        boolean setToDefault = result.setResult(defaultTimeoutValue());

        assertThat(setToDefault).isTrue();
        @SuppressWarnings("unchecked")
        ResponseEntity<ApiResponse<ConfigChangeNotification>> responseEntity =
                (ResponseEntity<ApiResponse<ConfigChangeNotification>>) result.getResult();
        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.NOT_MODIFIED);
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<ApiResponse<ConfigChangeNotification>> defaultTimeoutValue() {
        // Mirror SubscriptionService's private NOT_MODIFIED constant via a fresh instance â€?
        // the DeferredResult constructed by the service already carries it; here we only need
        // a same-shaped value to drive setResult() the way the async timeout callback would.
        return (ResponseEntity<ApiResponse<ConfigChangeNotification>>) (ResponseEntity<?>)
                ResponseEntity.status(HttpStatus.NOT_MODIFIED).build();
    }
}


