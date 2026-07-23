package com.lezai.samples.cache.config;

import com.lezai.samples.cache.core.CacheManager;
import com.lezai.samples.cache.impl.CaffeineCache;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class CaffeineAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CacheAutoConfiguration.class));

    @Test
    void globalCacheIsCaffeine_whenCaffeineOnClasspath() {
        runner.run(context -> {
            assertThat(context).hasBean("globalCache");
            assertThat(context.getBean("globalCache")).isInstanceOf(CaffeineCache.class);
            assertThat(context.getBean("cacheManager", CacheManager.class).getCache("X"))
                    .isInstanceOf(CaffeineCache.class);
        });
    }
}
