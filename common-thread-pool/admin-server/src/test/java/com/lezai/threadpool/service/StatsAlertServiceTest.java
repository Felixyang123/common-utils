package com.lezai.threadpool.service;

import com.lezai.threadpool.audit.AuditEvent;
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
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatsAlertServiceTest {

    @Mock
    private ThreadPoolStatsPersistenceService statsPersistenceService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private StatsAlertService service;

    @BeforeEach
    void setUp() {
        service = new StatsAlertService(statsPersistenceService, eventPublisher);
    }

    @Test
    @DisplayName("rejection rate > 5% triggers alert and publishes AuditEvent")
    void rejectionRateAlert() {
        when(statsPersistenceService.queryRecentStats(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(stats("app1", "pool-a", 100, 6, 10, 100)));

        var alerts = service.checkAlerts();

        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).getMetric()).isEqualTo("rejectionRate");
        assertThat(alerts.get(0).getValue()).isEqualTo(0.06d);
        verify(eventPublisher).publishEvent(any(AuditEvent.class));
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

        verify(eventPublisher, org.mockito.Mockito.times(1)).publishEvent(any(AuditEvent.class));
    }

    @Test
    @DisplayName("alert recovery publishes ALERT_RECOVERED AuditEvent")
    void recoveredAlertWritesLog() {
        when(statsPersistenceService.queryRecentStats(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(stats("app1", "pool-a", 100, 6, 10, 100)))
                .thenReturn(List.of(stats("app1", "pool-a", 100, 0, 10, 100)));

        service.checkAlerts();
        var recovered = service.checkAlerts();

        assertThat(recovered).isEmpty();
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(eventPublisher, times(2)).publishEvent(captor.capture());
        assertThat(captor.getAllValues().get(0).operateType()).isEqualTo(OperateType.ALERT.name());
        assertThat(captor.getAllValues().get(1).operateType()).isEqualTo(OperateType.ALERT_RECOVERED.name());
    }

    @Test
    @DisplayName("no abnormal metrics publishes no audit event")
    void noAlert() {
        when(statsPersistenceService.queryRecentStats(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(stats("app1", "pool-a", 100, 0, 10, 100)));

        var alerts = service.checkAlerts();

        assertThat(alerts).isEmpty();
        verify(eventPublisher, never()).publishEvent(any(AuditEvent.class));
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
