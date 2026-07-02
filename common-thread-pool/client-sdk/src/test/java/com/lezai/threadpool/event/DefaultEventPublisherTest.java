package com.lezai.threadpool.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DefaultEventPublisher")
class DefaultEventPublisherTest {

    @Test
    @DisplayName("publish invokes all registered listeners")
    void publish_invokesAllListeners() {
        AtomicInteger listener1Calls = new AtomicInteger();
        AtomicInteger listener2Calls = new AtomicInteger();
        DefaultEventPublisher publisher = new DefaultEventPublisher(List.of(
                event -> listener1Calls.incrementAndGet(),
                event -> listener2Calls.incrementAndGet()
        ));

        publisher.publish(ThreadPoolEvent.of(ThreadPoolEventType.POOL_CREATED, "pool-a", "test"));

        assertEquals(1, listener1Calls.get());
        assertEquals(1, listener2Calls.get());
    }

    @Test
    @DisplayName("a listener throwing does not prevent other listeners from running")
    void publish_listenerThrows_othersStillRun() {
        AtomicInteger goodListenerCalls = new AtomicInteger();
        ThreadPoolEventListener throwingListener = event -> { throw new RuntimeException("boom"); };
        ThreadPoolEventListener goodListener = event -> goodListenerCalls.incrementAndGet();

        DefaultEventPublisher publisher = new DefaultEventPublisher(List.of(throwingListener, goodListener));

        assertDoesNotThrow(() ->
                publisher.publish(ThreadPoolEvent.of(ThreadPoolEventType.POOL_CREATED, "pool-a", "test")));
        assertEquals(1, goodListenerCalls.get());
    }

    @Test
    @DisplayName("publish with no listeners does not throw")
    void publish_noListeners_doesNotThrow() {
        DefaultEventPublisher publisher = new DefaultEventPublisher(List.of());

        assertDoesNotThrow(() ->
                publisher.publish(ThreadPoolEvent.of(ThreadPoolEventType.POOL_CREATED, "pool-a", "test")));
    }
}
