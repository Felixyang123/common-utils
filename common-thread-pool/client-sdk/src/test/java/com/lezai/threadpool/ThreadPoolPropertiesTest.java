package com.lezai.threadpool;

import com.lezai.threadpool.properties.ThreadPoolProperties;
import jakarta.validation.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ThreadPoolProperties")
class ThreadPoolPropertiesTest {

    /** 将 Map 绑定到 thread.pool.* 并返回实例；空 map 返回默认实例 */
    ThreadPoolProperties bind(Map<String, String> props) {
        var binder = new Binder(
                new MapConfigurationPropertySource(props)
        );
        BindResult<ThreadPoolProperties> result = binder.bind("thread.pool", ThreadPoolProperties.class);
        return result.orElseGet(ThreadPoolProperties::new);
    }

    @Test
    @DisplayName("default remote.enabled is false")
    void remoteEnabledDefaultsFalse() {
        var p = bind(Map.of());
        assertFalse(p.getRemote().isEnabled());
    }

    @Test
    @DisplayName("server-url defaults to http://localhost:8080")
    void serverUrlDefaults() {
        var p = bind(Map.of());
        assertEquals("http://localhost:8080", p.getRemote().getServerUrl());
    }

    @Test
    @DisplayName("remote server-url, app-id, api-key bind correctly when enabled")
    void remoteClientConfigBinds() {
        var p = bind(Map.of(
                "thread.pool.remote.enabled", "true",
                "thread.pool.remote.server-url", "http://admin:9090",
                "thread.pool.remote.app-id", "my-app",
                "thread.pool.remote.api-key", "sk-123"
        ));
        assertTrue(p.getRemote().isEnabled());
        assertEquals("http://admin:9090", p.getRemote().getServerUrl());
        assertEquals("my-app", p.getRemote().getAppId());
        assertEquals("sk-123", p.getRemote().getApiKey());
    }

    @Test
    @DisplayName("remote.enabled=true with missing api-key fails validation")
    void remoteEnabledMissingApiKeyFails() {
        var p = bind(Map.of(
                "thread.pool.remote.enabled", "true",
                "thread.pool.remote.server-url", "http://localhost:8080",
                "thread.pool.remote.app-id", "my-app"
        ));
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var violations = validator.validate(p);
            assertFalse(violations.isEmpty(), "should have validation violations when api-key is missing and enabled=true");
        }
    }

    @Test
    @DisplayName("remote.enabled=false with missing api-key passes validation")
    void remoteDisabledNoValidations() {
        var p = bind(Map.of());  // enabled=false (default), no api-key
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var violations = validator.validate(p);
            assertTrue(violations.isEmpty(), "no validation violations when remote is disabled");
        }
    }

    @Test
    @DisplayName("pullIntervalMs defaults to 0 (disabled)")
    void pullIntervalMsDefaultsZero() {
        var p = bind(Map.of());
        assertEquals(0L, p.getRemote().getPullIntervalMs());
    }

    @Test
    @DisplayName("pool config binds correctly")
    void poolConfigBinds() {
        var p = bind(Map.of(
                "thread.pool.pools[0].name", "order-pool",
                "thread.pool.pools[0].core-pool-size", "5",
                "thread.pool.pools[0].maximum-pool-size", "10",
                "thread.pool.pools[0].queue-capacity", "200"
        ));
        assertEquals(1, p.getPools().length);
        assertEquals("order-pool", p.getPools()[0].getName());
        assertEquals(5, p.getPools()[0].getCorePoolSize());
        assertEquals(10, p.getPools()[0].getMaximumPoolSize());
        assertEquals(200, p.getPools()[0].getQueueCapacity());
    }
}
