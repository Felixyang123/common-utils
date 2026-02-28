package com.wly.samples.schedule;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.context.scope.refresh.RefreshScopeRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.util.concurrent.ScheduledFuture;

@Component
@Slf4j
public class NacosDynamicCronTask {

    @Resource
    private ScheduleTaskPropConfig scheduleTaskPropConfig;

    private ScheduledFuture<?> scheduledFuture;
    private final ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();

    @PostConstruct
    public void init() {
        taskScheduler.initialize();
        startTask(scheduleTaskPropConfig.getCron()); // 初始值
    }

    public void startTask(String cron) {
        scheduledFuture = taskScheduler.schedule(() -> log.info("NacosDynamicCronTask"), new CronTrigger(cron));
    }

    // 监听 Nacos 配置变更
    @EventListener
    public void handleConfigRefresh(RefreshScopeRefreshedEvent event) {
        updateCron(scheduleTaskPropConfig.getCron());
    }

    public synchronized void updateCron(String newCron) {
        if (scheduledFuture != null) {
            scheduledFuture.cancel(false);
        }
        startTask(newCron);
    }
}