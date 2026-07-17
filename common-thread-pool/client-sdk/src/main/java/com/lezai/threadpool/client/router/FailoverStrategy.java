package com.lezai.threadpool.client.router;

import java.util.List;

@RoutingAlgorithmType(RoutingAlgorithm.FAILOVER)
public class FailoverStrategy implements RoutingStrategy {

    private volatile String activeBaseUrl;

    @Override
    public synchronized ServerNode select(List<ServerNode> candidates) {
        if (activeBaseUrl != null) {
            for (ServerNode c : candidates) {
                if (c.getBaseUrl().equals(activeBaseUrl)) {
                    return c;
                }
            }
        }
        ServerNode chosen = candidates.getFirst();
        activeBaseUrl = chosen.getBaseUrl();
        return chosen;
    }
}
