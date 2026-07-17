package com.lezai.threadpool.client.router;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@RoutingAlgorithmType(RoutingAlgorithm.ROUND_ROBIN)
public class RoundRobinStrategy implements RoutingStrategy {

    private final AtomicInteger counter = new AtomicInteger();

    @Override
    public ServerNode select(List<ServerNode> candidates) {
        return candidates.get(Math.floorMod(counter.getAndIncrement(), candidates.size()));
    }
}
