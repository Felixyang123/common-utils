package com.lezai.threadpool.client.router;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RoutingAlgorithmType(RoutingAlgorithm.FAILOVER)
public class FailoverStrategy implements RoutingStrategy {

    private String activeBaseUrl;

    @Override
    public ServerNode select(List<ServerNode> candidates) {
        if (activeBaseUrl != null) {
            for (ServerNode c : candidates) {
                if (c.getBaseUrl().equals(activeBaseUrl)) {
                    return c;
                }
            }
        }
        ServerNode chosen = candidates.get(0);
        activeBaseUrl = chosen.getBaseUrl();
        return chosen;
    }
}
