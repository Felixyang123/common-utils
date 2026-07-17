package com.lezai.threadpool.client.router;

import com.lezai.threadpool.bean.AddConfigAppResult;
import com.lezai.threadpool.bean.ConfigChangeNotification;
import com.lezai.threadpool.bean.ThreadPoolConfig;
import com.lezai.threadpool.bean.ThreadPoolConfigResp;
import com.lezai.threadpool.bean.ThreadPoolStatsReport;
import com.lezai.threadpool.client.ConfigOperations;
import com.lezai.threadpool.utils.LogEvents;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.List;

@Slf4j
public class FailoverRouter implements ConfigOperations {

    private final NodeManager nodeManager;
    private final RoutingStrategyFactory strategyFactory;

    public FailoverRouter(NodeManager nodeManager, RoutingStrategyFactory strategyFactory) {
        this.nodeManager = nodeManager;
        this.strategyFactory = strategyFactory;
    }

    @Override
    public ConfigChangeNotification subscribe(long version, long timeoutMs) throws IOException {
        return execute(node -> node.subscribe(version, timeoutMs));
    }

    @Override
    public ThreadPoolConfigResp pullConfigs(Long version) throws IOException {
        return execute(node -> node.pullConfigs(version));
    }

    @Override
    public AddConfigAppResult registerConfig(ThreadPoolConfig config) throws IOException {
        return execute(node -> node.registerConfig(config));
    }

    @Override
    public AddConfigAppResult registerConfigs(List<ThreadPoolConfig> configs) throws IOException {
        return execute(node -> node.registerConfigs(configs));
    }

    @Override
    public void reportStats(ThreadPoolStatsReport report) throws IOException {
        execute(node -> {
            node.reportStats(report);
            return null;
        });
    }

    private <T> T execute(IoNodeCall<T> call) throws IOException {
        IOException last = null;
        ServerNode lastNode = null;
        for (int attempt = 0; attempt < nodeManager.nodeCount(); attempt++) {
            List<ServerNode> candidates = nodeManager.getCandidates();
            if (candidates.isEmpty()) {
                break;
            }
            ServerNode node = strategyFactory.getStrategy().select(candidates);
            try {
                return call.apply(node);
            } catch (IOException e) {
                last = e;
                if (lastNode != null && !lastNode.getBaseUrl().equals(node.getBaseUrl())) {
                    LogEvents.failover(lastNode.getBaseUrl(), node.getBaseUrl(), e.getMessage());
                }
                lastNode = node;
            }
        }
        throw last != null ? last : new IOException("No available admin-server node");
    }

    @Override
    public void release() {
        nodeManager.release();
    }

    @FunctionalInterface
    private interface IoNodeCall<T> {
        T apply(ServerNode node) throws IOException;
    }
}
