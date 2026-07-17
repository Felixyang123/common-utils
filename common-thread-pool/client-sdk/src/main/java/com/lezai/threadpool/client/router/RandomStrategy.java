package com.lezai.threadpool.client.router;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Component
@RoutingAlgorithmType(RoutingAlgorithm.RANDOM)
public class RandomStrategy implements RoutingStrategy {

    @Override
    public ServerNode select(List<ServerNode> candidates) {
        return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
    }
}
