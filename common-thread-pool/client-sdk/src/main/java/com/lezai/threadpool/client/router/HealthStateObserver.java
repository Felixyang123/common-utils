package com.lezai.threadpool.client.router;

public interface HealthStateObserver {
    void onStatusChanged(ServerNode node, NodeHealthStatus newStatus);
}
