package com.lezai.threadpool.client.router;

import com.lezai.threadpool.properties.ThreadPoolProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

@Slf4j
@RequiredArgsConstructor
public class RoutingStrategyFactory {

    private final ThreadPoolProperties properties;
    private final Map<RoutingAlgorithm, RoutingStrategy> strategies;

    public RoutingStrategyFactory(ThreadPoolProperties properties, List<RoutingStrategy> strategyList) {
        this.properties = properties;
        this.strategies = strategyList.stream()
                .collect(toMap(s -> s.getClass().getAnnotation(RoutingAlgorithmType.class).value(), identity()));
    }

    public RoutingStrategy getStrategy() {
        RoutingAlgorithm algorithm = RoutingAlgorithm.valueOf(
                properties.getRemote().getRoutingAlgorithm().toUpperCase().replace('-', '_'));
        RoutingStrategy strategy = strategies.get(algorithm);
        if (strategy == null) {
            log.warn("No strategy registered for algorithm {}, falling back to round-robin", algorithm);
            return strategies.get(RoutingAlgorithm.ROUND_ROBIN);
        }
        return strategy;
    }
}
