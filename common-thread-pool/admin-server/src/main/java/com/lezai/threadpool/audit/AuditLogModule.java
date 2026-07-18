package com.lezai.threadpool.audit;

import com.lezai.threadpool.enums.BizType;
import com.lezai.threadpool.enums.OperateType;
import com.lezai.threadpool.service.OperateLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuditLogModule {

    private final OperateLogService operateLogService;

    @EventListener
    @Async("historyRecordExecutor")
    public void onAuditEvent(AuditEvent event) {
        try {
            OperateType operateType = OperateType.valueOf(event.operateType());
            BizType bizType = BizType.valueOf(event.bizType());
            operateLogService.log(operateType, event.operator(), event.content(), event.bizId(), bizType);
        } catch (Exception e) {
            log.error("Failed to record audit event: {}", event, e);
        }
    }
}
