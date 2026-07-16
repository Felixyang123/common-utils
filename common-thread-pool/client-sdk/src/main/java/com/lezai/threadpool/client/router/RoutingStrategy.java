package com.lezai.threadpool.client.router;

import java.util.List;

public interface RoutingStrategy {
    ServerNode select(List<ServerNode> candidates);
}
