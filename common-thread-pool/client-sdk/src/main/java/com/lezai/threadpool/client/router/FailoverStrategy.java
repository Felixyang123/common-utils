package com.lezai.threadpool.client.router;

import java.util.List;

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
