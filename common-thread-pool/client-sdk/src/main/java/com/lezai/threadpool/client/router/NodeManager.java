package com.lezai.threadpool.client.router;

import java.util.List;

public interface NodeManager extends HealthStateObserver, CircuitBreakerObserver {
    List<ServerNode> getCandidates();
    boolean isDegraded();
    int nodeCount();
    void release();
}
