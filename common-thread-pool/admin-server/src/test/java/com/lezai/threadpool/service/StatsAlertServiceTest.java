package com.lezai.threadpool.service;

import com.lezai.threadpool.dao.entity.ThreadPoolStatsEntity;
import com.lezai.threadpool.enums.BizType;
import com.lezai.threadpool.enums.OperateType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatsAlertServiceTest {

    @Mock
    private ThreadPoolStatsPersistenceService statsPersistenceService;

    @Mock
    private OperateLogService operateLogService;

    private StatsAlertService service;

    @BeforeEach
    void setUp() {
        service = new StatsAlertService(statsPersistenceService, operateLogService);
    }

    @Test
    @DisplayName("rejection rate > 5% triggers alert and writes audit log")
    void rejectionRateAlert() {
        when(statsPersistenceService.queryRecentStats(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(stats("app1", "pool-a", 100, 6, 10, 100)));

        var alerts = service.checkAlerts();

        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).getMetric()).isEqualTo("rejectionRate");
        assertThat(alerts.get(0).getValue()).isEqualTo(0.06d);
        verify(operateLogService).log(eq(OperateType.ALERT), eq("system"), eq(alerts.get(0)), eq("app1:pool-a:rejectionRate"), eq(BizType.THREAD_POOL_STATS));
    }

    @Test
    @DisplayName("queue usage > 80% triggers alert")
    void queueUsageAlert() {
        when(statsPersistenceService.queryRecentStats(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(stats("app1", "pool-a", 100, 0, 81, 100)));

        var alerts = service.checkAlerts();

        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).getMetric()).isEqualTo("queueUsageRate");
        assertThat(alerts.get(0).getValue()).isEqualTo(0.81d);
    }

    @Test
    @DisplayName("same app/pool/metric deduplicates within 30 minutes")
    void deduplicateWithinThirtyMinutes() {
        when(statsPersistenceService.queryRecentStats(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(stats("app1", "pool-a", 100, 6, 10, 100)));

        service.checkAlerts();
        service.checkAlerts();

        verify(operateLogService, org.mockito.Mockito.times(1))
                .log(eq(OperateType.ALERT), eq("system"), org.mockito.ArgumentMatchers.any(), eq("app1:pool-a:rejectionRate"), eq(BizType.THREAD_POOL_STATS));
    }

    @Test
    @DisplayName("alert recovery writes ALERT_RECOVERED log")
    void recoveredAlertWritesLog() {
        when(statsPersistenceService.queryRecentStats(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(stats("app1", "pool-a", 100, 6, 10, 100)))
                .thenReturn(List.of(stats("app1", "pool-a", 100, 0, 10, 100)));

        service.checkAlerts();
        var recovered = service.checkAlerts();

        assertThat(recovered).isEmpty();
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(operateLogService).log(eq(OperateType.ALERT_RECOVERED), eq("system"), captor.capture(), eq("app1:pool-a:rejectionRate"), eq(BizType.THREAD_POOL_STATS));
    }

    @Test
    @DisplayName("no abnormal metrics writes no alert log")
    void noAlert() {
        when(statsPersistenceService.queryRecentStats(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(stats("app1", "pool-a", 100, 0, 10, 100)));

        var alerts = service.checkAlerts();

        assertThat(alerts).isEmpty();
        verify(operateLogService, never()).log(eq(OperateType.ALERT), eq("system"), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), eq(BizType.THREAD_POOL_STATS));
    }

    private ThreadPoolStatsEntity stats(String appId, String poolName, long submitted, long rejected,
                                        int queueSize, int queueCapacity) {
        ThreadPoolStatsEntity entity = new ThreadPoolStatsEntity();
        entity.setAppId(appId);
        entity.setPoolName(poolName);
        entity.setSubmittedTaskCount(submitted);
        entity.setRejectedTaskCount(rejected);
        entity.setQueueSize(queueSize);
        entity.setQueueCapacity(queueCapacity);
        entity.setCollectTime(LocalDateTime.now());
        return entity;
    }
}
