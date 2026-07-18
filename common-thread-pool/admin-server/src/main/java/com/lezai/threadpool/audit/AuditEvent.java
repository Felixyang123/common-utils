package com.lezai.threadpool.audit;

public record AuditEvent(
        String bizType,
        String operateType,
        String operator,
        Object content,
        String bizId) {
}
