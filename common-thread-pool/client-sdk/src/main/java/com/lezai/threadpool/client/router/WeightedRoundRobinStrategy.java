package com.lezai.threadpool.client.router;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@RoutingAlgorithmType(RoutingAlgorithm.WEIGHTED_ROUND_ROBIN)
public class WeightedRoundRobinStrategy implements RoutingStrategy {

    private final AtomicInteger counter = new AtomicInteger();

    @Override
    public ServerNode select(List<ServerNode> candidates) {
        List<ServerNode> weighted = new ArrayList<>();
        for (ServerNode node : candidates) {
            for (int i = 0; i < node.getWeight(); i++) {
                weighted.add(node);
            }
        }
        return weighted.get(Math.floorMod(counter.getAndIncrement(), weighted.size()));
    }
}
