package com.lezai.threadpool.manager;

import com.lezai.threadpool.bean.ThreadPoolConfig;
import com.lezai.threadpool.core.DynamicThreadPoolWrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 {@link ConcurrentHashMap#computeIfAbsent} 的 JDK 9+ 约束（见 CONTEXT.md §运行时事件）：
 * <ul>
 *   <li>mapping function 内对同一 map 做结构性修改（put/remove）会抛 {@link IllegalStateException}（"Recursive update"）</li>
 *   <li>mapping function 内读 {@code values()} <b>不会</b>抛异常——因此事件/监听器通知被移出 compute lambda
 *       的主因是<b>防并发死锁</b>，而非防异常</li>
 * </ul>
 * 该行为是 {@link ThreadPoolManager#registerPool} 把事件/监听器通知放在 compute lambda 之外的依据。
 */
@DisplayName("ThreadPoolManager computeIfAbsent 死锁约束")
class ThreadPoolManagerReentrancyTest {

    private static ConcurrentHashMap<String, DynamicThreadPoolWrapper> newRegistry() {
        return new ConcurrentHashMap<>();
    }

    private static DynamicThreadPoolWrapper wrapper(String name) {
        return new DynamicThreadPoolWrapper(ThreadPoolConfig.builder()
                .poolName(name).corePoolSize(1).maximumPoolSize(1).queueCapacity(1).build());
    }

    @Test
    @DisplayName("computeIfAbsent: mapping function 内结构性修改同一 key 抛 IllegalStateException (JDK 9+)")
    void computeIfAbsent_structuralModification_throws() {
        ConcurrentHashMap<String, DynamicThreadPoolWrapper> registry = newRegistry();

        // JDK 21 的 "Recursive update" 检测针对的是正在计算的同一 key：
        // mapping function 内对同一 key 做 put / 递归 computeIfAbsent，会抛 IllegalStateException。
        // （对其它 key 的 put 即使触发 rehash 也不抛，故此处用同一 key 作为可靠触发条件。）
        assertThatThrownBy(() -> registry.computeIfAbsent("trigger", key -> {
            // mapping function 内对同一 key 结构性修改 (put) → JDK 9+ 抛 IllegalStateException
            registry.put(key, wrapper(key));
            return wrapper(key);
        })).isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Recursive update");
    }

    @Test
    @DisplayName("computeIfAbsent: mapping function 内读 values() 不抛异常（安全，移出 lambda 是防死锁）")
    void computeIfAbsent_readingValues_doesNotThrow() {
        ConcurrentHashMap<String, DynamicThreadPoolWrapper> registry = newRegistry();
        registry.put("existing", wrapper("existing"));

        // 读操作不抛异常 — 证明移出 lambda 的主因是防并发死锁，而非防异常
        assertThatCode(() -> registry.computeIfAbsent("newKey", key -> {
            registry.values(); // read — safe on JDK 21
            return wrapper(key);
        })).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("正常 registerPool 不在 compute lambda 内回调，应成功创建")
    void registerPool_withoutReentrantCallback_succeeds() {
        ThreadPoolManager manager = new ThreadPoolManager();
        ThreadPoolConfig config = ThreadPoolConfig.builder()
                .poolName("reentrant-pool")
                .corePoolSize(1)
                .maximumPoolSize(2)
                .queueCapacity(10)
                .build();

        var pool = manager.registerPool(config);

        var loaded = manager.getRequiredPool("reentrant-pool");
        assertThat(loaded).isSameAs(pool);
        manager.shutdownNow();
    }
}
