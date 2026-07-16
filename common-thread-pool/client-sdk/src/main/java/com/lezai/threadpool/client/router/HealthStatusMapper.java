package com.lezai.threadpool.client.router;

public final class HealthStatusMapper {

    private HealthStatusMapper() {
    }

    public static NodeHealthStatus fromResponse(String status) {
        if (status == null) {
            return NodeHealthStatus.UNKNOWN;
        }
        return switch (status.toUpperCase()) {
            case "UP" -> NodeHealthStatus.UP;
            case "DEGRADED" -> NodeHealthStatus.DEGRADED;
            case "DOWN" -> NodeHealthStatus.DOWN;
            default -> NodeHealthStatus.UNKNOWN;
        };
    }
}
