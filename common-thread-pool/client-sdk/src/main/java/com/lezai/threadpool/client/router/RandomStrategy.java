package com.lezai.threadpool.client.router;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class RandomStrategy implements RoutingStrategy {

    @Override
    public ServerNode select(List<ServerNode> candidates) {
        return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
    }
}
