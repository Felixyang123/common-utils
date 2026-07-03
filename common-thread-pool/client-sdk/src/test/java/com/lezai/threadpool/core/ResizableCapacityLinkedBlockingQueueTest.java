package com.lezai.threadpool.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ResizableCapacityLinkedBlockingQueue")
class ResizableCapacityLinkedBlockingQueueTest {

    @Test
    @DisplayName("put blocks when queue is full")
    void putBlocksWhenFull() throws Exception {
        ResizableCapacityLinkedBlockingQueue<String> queue = new ResizableCapacityLinkedBlockingQueue<>(1);
        queue.put("a");

        CountDownLatch started = new CountDownLatch(1);
        Thread t = new Thread(() -> {
            started.countDown();
            try { queue.put("b"); } catch (InterruptedException ignored) {}
        });
        t.start();
        assertTrue(started.await(1, TimeUnit.SECONDS));
        Thread.sleep(100);
        assertTrue(t.isAlive(), "put should block when queue is full");

        queue.take();
        t.join(2000);
        assertFalse(t.isAlive(), "put should unblock after take frees a slot");
    }

    @Test
    @DisplayName("take blocks when queue is empty")
    void takeBlocksWhenEmpty() throws Exception {
        ResizableCapacityLinkedBlockingQueue<String> queue = new ResizableCapacityLinkedBlockingQueue<>(1);

        CountDownLatch started = new CountDownLatch(1);
        Thread t = new Thread(() -> {
            started.countDown();
            try { queue.take(); } catch (InterruptedException ignored) {}
        });
        t.start();
        assertTrue(started.await(1, TimeUnit.SECONDS));
        Thread.sleep(100);
        assertTrue(t.isAlive(), "take should block when queue is empty");

        queue.put("x");
        t.join(2000);
        assertFalse(t.isAlive(), "take should unblock after put");
    }

    @Test
    @DisplayName("setCapacity expanding wakes blocked put threads")
    void setCapacityExpandWakesPut() throws Exception {
        ResizableCapacityLinkedBlockingQueue<String> queue = new ResizableCapacityLinkedBlockingQueue<>(1);
        queue.put("a");

        CountDownLatch unblocked = new CountDownLatch(1);
        Thread t = new Thread(() -> {
            try { queue.put("b"); unblocked.countDown(); } catch (InterruptedException ignored) {}
        });
        t.start();
        Thread.sleep(100);

        queue.setCapacity(5);
        assertTrue(unblocked.await(2, TimeUnit.SECONDS),
                "expanding capacity should wake blocked put threads");
        t.join(2000);
    }

    @Test
    @DisplayName("setCapacity shrinking allows existing items to drain normally")
    void setCapacityShrinkDrainsExisting() throws Exception {
        ResizableCapacityLinkedBlockingQueue<String> queue = new ResizableCapacityLinkedBlockingQueue<>(5);
        queue.put("a");
        queue.put("b");
        queue.put("c");

        queue.setCapacity(2);
        assertEquals(3, queue.size(), "existing items should not be dropped");

        assertEquals("a", queue.take());
        assertEquals("b", queue.take());
        assertEquals("c", queue.take());
    }

    @Test
    @DisplayName("remainingCapacity reflects new capacity after setCapacity")
    void remainingCapacityAfterResize() {
        ResizableCapacityLinkedBlockingQueue<String> queue = new ResizableCapacityLinkedBlockingQueue<>(3);
        assertEquals(3, queue.remainingCapacity());

        queue.setCapacity(10);
        assertEquals(10, queue.remainingCapacity());

        queue.setCapacity(1);
        assertEquals(1, queue.remainingCapacity());
    }

    @Test
    @DisplayName("setCapacity rejects zero or negative capacity")
    void setCapacityRejectsInvalid() {
        ResizableCapacityLinkedBlockingQueue<String> queue = new ResizableCapacityLinkedBlockingQueue<>(3);
        assertThrows(IllegalArgumentException.class, () -> queue.setCapacity(0));
        assertThrows(IllegalArgumentException.class, () -> queue.setCapacity(-1));
    }

    @Test
    @DisplayName("size() and capacity() return correct values")
    void sizeAndCapacity() {
        ResizableCapacityLinkedBlockingQueue<String> queue = new ResizableCapacityLinkedBlockingQueue<>(3);
        assertEquals(3, queue.capacity());
        assertEquals(0, queue.size());

        queue.offer("a");
        queue.offer("b");
        assertEquals(2, queue.size());
        assertEquals(3, queue.capacity());
    }
}