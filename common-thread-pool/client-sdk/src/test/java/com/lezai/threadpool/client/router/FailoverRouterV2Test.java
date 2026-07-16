package com.lezai.threadpool.client.router;

import com.lezai.threadpool.bean.ThreadPoolConfigResp;
import com.lezai.threadpool.client.ConfigServerClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class FailoverRouterV2Test {

    private ServerNode upNode(MockWebServer server) {
        String baseUrl = server.url("/").toString().replaceAll("/$", "");
        ConfigServerClient client = new ConfigServerClient(baseUrl, "app", "key", 1000);
        ServerNode node = new ServerNode(baseUrl, baseUrl, 1, client, new CircuitBreaker(3, 30000));
        node.markHealth(NodeHealthStatus.UP);
        return node;
    }

    @Test
    void selectsNodeAndDelegatesCall() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"code\":0,\"message\":\"success\",\"data\":{\"configVersion\":1,\"configs\":[]}}"));
            server.start();
            ServerNode node = upNode(server);

            NodeManager nodeManager = mock(NodeManager.class);
            when(nodeManager.getCandidates()).thenReturn(List.of(node));
            when(nodeManager.nodeCount()).thenReturn(1);

            RoutingStrategy strategy = mock(RoutingStrategy.class);
            when(strategy.select(List.of(node))).thenReturn(node);

            FailoverRouter router = new FailoverRouter(nodeManager, strategy);
            ThreadPoolConfigResp resp = router.pullConfigs(1L);

            assertThat(resp.getConfigVersion()).isEqualTo(1);
            verify(strategy).select(List.of(node));
            verify(nodeManager).getCandidates();
            router.release();
        }
    }

    @Test
    void retriesOnNextNodeAfterIOException() throws Exception {
        try (MockWebServer goodServer = new MockWebServer(); MockWebServer badServer = new MockWebServer()) {
            goodServer.enqueue(new MockResponse().setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"code\":0,\"message\":\"success\",\"data\":{\"configVersion\":5,\"configs\":[]}}"));
            badServer.enqueue(new MockResponse().setResponseCode(500).setBody(""));
            goodServer.start();
            badServer.start();

            ServerNode badNode = upNode(badServer);
            ServerNode goodNode = upNode(goodServer);

            NodeManager nodeManager = mock(NodeManager.class);
            when(nodeManager.getCandidates()).thenReturn(List.of(badNode, goodNode));
            when(nodeManager.nodeCount()).thenReturn(2);

            RoutingStrategy strategy = mock(RoutingStrategy.class);
            when(strategy.select(List.of(badNode, goodNode)))
                    .thenReturn(badNode)
                    .thenReturn(goodNode);

            FailoverRouter router = new FailoverRouter(nodeManager, strategy);
            ThreadPoolConfigResp resp = router.pullConfigs(null);

            assertThat(resp.getConfigVersion()).isEqualTo(5);
            verify(strategy, times(2)).select(List.of(badNode, goodNode));
            router.release();
        }
    }

    @Test
    void throwsAfterAllNodesExhausted() throws Exception {
        try (MockWebServer server1 = new MockWebServer(); MockWebServer server2 = new MockWebServer()) {
            server1.start();
            server2.start();

            ServerNode n1 = upNode(server1);
            ServerNode n2 = upNode(server2);

            NodeManager nodeManager = mock(NodeManager.class);
            when(nodeManager.getCandidates()).thenReturn(List.of(n1, n2));
            when(nodeManager.nodeCount()).thenReturn(2);

            RoutingStrategy strategy = mock(RoutingStrategy.class);
            when(strategy.select(anyList())).thenReturn(n1);

            FailoverRouter router = new FailoverRouter(nodeManager, strategy);
            assertThatThrownBy(() -> router.pullConfigs(null)).isInstanceOf(IOException.class);
            router.release();
        }
    }

    @Test
    void noCandidatesThrowsImmediately() {
        NodeManager nodeManager = mock(NodeManager.class);
        when(nodeManager.getCandidates()).thenReturn(List.of());
        when(nodeManager.nodeCount()).thenReturn(0);

        RoutingStrategy strategy = mock(RoutingStrategy.class);

        FailoverRouter router = new FailoverRouter(nodeManager, strategy);
        assertThatThrownBy(() -> router.pullConfigs(null)).isInstanceOf(IOException.class);
        verify(strategy, never()).select(anyList());
        router.release();
    }
}
