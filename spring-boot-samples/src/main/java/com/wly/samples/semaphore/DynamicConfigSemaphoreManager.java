package com.wly.samples.semaphore;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

import javax.security.auth.DestroyFailedException;
import javax.security.auth.Destroyable;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Component
@ConfigurationProperties(prefix = "semaphore")
@RefreshScope
@Slf4j
public class DynamicConfigSemaphoreManager implements Destroyable {

    @Setter
    @Getter
    private Integer interval;

    private static ScheduledExecutorService scheduledExecutor;

    @PostConstruct
    public void printInterval() {
        Optional.ofNullable(scheduledExecutor).ifPresent(ScheduledExecutorService::shutdownNow);
        scheduledExecutor = Executors.newSingleThreadScheduledExecutor();
//        scheduledExecutor.scheduleAtFixedRate(() -> log.info("interval: {}", interval), 0, 2, TimeUnit.SECONDS);
    }

    @Override
    public void destroy() throws DestroyFailedException {
        Optional.ofNullable(scheduledExecutor).ifPresent(exe -> {
            log.info("SemaphoreManager shutdown...");
            exe.shutdownNow();
        });
     }
}
