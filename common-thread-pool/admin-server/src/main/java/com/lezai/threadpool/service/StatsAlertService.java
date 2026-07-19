package com.lezai.threadpool.service;

import com.lezai.threadpool.audit.AuditEvent;
import com.lezai.threadpool.dao.entity.ThreadPoolStatsEntity;
import com.lezai.threadpool.enums.BizType;
import com.lezai.threadpool.enums.OperateType;
import com.lezai.threadpool.pojo.bean.PoolAlert;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatsAlertService {

    static final double REJECTION_RATE_THRESHOLD = 0.05d;
    static final double QUEUE_USAGE_THRESHOLD = 0.80d;

    private final ThreadPoolStatsPersistenceService statsPersistenceService;
    private final ApplicationEventPublisher eventPublisher;
    private final Map<String, LocalDateTime> lastAlertTime = new ConcurrentHashMap<>();
    private final Map<String, PoolAlert> activeAlerts = new ConcurrentHashMap<>();

    public List<PoolAlert> checkAlerts() {
        LocalDateTime now = LocalDateTime.now();
        List<ThreadPoolStatsEntity> recentStats = statsPersistenceService.queryRecentStats(now.minusMinutes(5));
        Map<String, List<ThreadPoolStatsEntity>> grouped = recentStats.stream()
                .filter(stat -> stat.getAppId() != null && stat.getPoolName() != null)
                .collect(Collectors.groupingBy(stat -> stat.getAppId() + ":" + stat.getPoolName()));

        List<PoolAlert> alerts = new ArrayList<>();
        for (Map.Entry<String, List<ThreadPoolStatsEntity>> entry : grouped.entrySet()) {
            alerts.addAll(checkPool(entry.getValue(), now));
        }
        recoverMissingAlerts(alerts, now);
        return alerts;
    }

    private List<PoolAlert> checkPool(List<ThreadPoolStatsEntity> stats, LocalDateTime now) {
        ThreadPoolStatsEntity first = stats.get(0);
        List<PoolAlert> alerts = new ArrayList<>();
        long submitted = stats.stream().mapToLong(ThreadPoolStatsEntity::getSubmittedTaskCount).sum();
        long rejected = stats.stream().mapToLong(ThreadPoolStatsEntity::getRejectedTaskCount).sum();
        double rejectionRate = submitted > 0 ? (double) rejected / submitted : 0.0d;
        if (rejectionRate > REJECTION_RATE_THRESHOLD) {
            alerts.add(alert(first.getAppId(), first.getPoolName(), "rejectionRate", rejectionRate,
                    REJECTION_RATE_THRESHOLD, now, "thread pool rejection rate exceeds threshold"));
        }

        double queueUsage = stats.stream()
                .filter(stat -> stat.getQueueCapacity() > 0)
                .mapToDouble(stat -> (double) stat.getQueueSize() / stat.getQueueCapacity())
                .average()
                .orElse(0.0d);
        if (queueUsage > QUEUE_USAGE_THRESHOLD) {
            alerts.add(alert(first.getAppId(), first.getPoolName(), "queueUsageRate", queueUsage,
                    QUEUE_USAGE_THRESHOLD, now, "thread pool queue usage rate exceeds threshold"));
        }

        for (PoolAlert alert : alerts) {
            recordAlertIfNeeded(alert, now);
        }
        return alerts;
    }

    private PoolAlert alert(String appId, String poolName, String metric, double value, double threshold,
                            LocalDateTime detectedAt, String message) {
        return PoolAlert.builder()
                .appId(appId)
                .poolName(poolName)
                .metric(metric)
                .value(value)
                .threshold(threshold)
                .detectedAt(detectedAt)
                .message(message)
                .build();
    }

    private void recordAlertIfNeeded(PoolAlert alert, LocalDateTime now) {
        String key = key(alert);
        activeAlerts.put(key, alert);
        synchronized (lastAlertTime) {
            LocalDateTime last = lastAlertTime.get(key);
            if (last == null || last.plusMinutes(30).isBefore(now)) {
                eventPublisher.publishEvent(new AuditEvent(BizType.THREAD_POOL_STATS.name(), OperateType.ALERT.name(),
                        "system", alert, key));
                lastAlertTime.put(key, now);
            }
        }
    }

    private void recoverMissingAlerts(List<PoolAlert> currentAlerts, LocalDateTime now) {
        List<String> currentKeys = currentAlerts.stream().map(this::key).toList();
        List<String> recoveredKeys = activeAlerts.keySet().stream()
                .filter(key -> !currentKeys.contains(key))
                .toList();
        for (String key : recoveredKeys) {
            PoolAlert recovered = activeAlerts.remove(key);
            if (recovered != null) {
                eventPublisher.publishEvent(new AuditEvent(BizType.THREAD_POOL_STATS.name(), OperateType.ALERT_RECOVERED.name(),
                        "system", recovered, key));
                lastAlertTime.remove(key);
                log.info("Thread pool alert recovered: {}, time={}", key, now);
            }
        }
    }

    private String key(PoolAlert alert) {
        return String.join(":", alert.getAppId(), alert.getPoolName(), alert.getMetric());
    }
}
