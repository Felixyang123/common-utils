package com.lezai.samples.cache;

import com.lezai.lock.LocalLock;
import com.lezai.lock.LockSupport;
import com.lezai.samples.cache.impl.HashMapCache;
import com.lezai.samples.cache.impl.HashMapCacheManager;
import com.lezai.samples.cache.impl.MultiHashMapCache;
import com.lezai.samples.cache.impl.MultiHashMapCacheManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

public class CacheTest {

    @Test
    void HashMapCache_Prototype_Test() {
        record User(String name, int age) {
        }
        long ttl = 60 * 1000;
        HashMapCache<Object> cache = new HashMapCache<>(1000, ttl, "");
        CacheManager cacheManager = new HashMapCacheManager(cache, 1000, ttl);
        CacheSupport<User> userCache = new CacheSupport<>(cacheManager) {
            @Override
            public User load(String key) {
                return new User("xiaoming", 18);
            }

            @Override
            public String category() {
                return "user";
            }

            @Override
            public Long ttl() {
                return ttl;
            }
        };

        User user = userCache.get("xiaoming");
        Assertions.assertNull(user);

        ReflectionTestUtils.setField(LockSupport.class, "lock", new LocalLock());

        user = userCache.safetyGet("xiaoming", 1000);
        Assertions.assertNotNull(user);
        Assertions.assertEquals("xiaoming", user.name);

        record Order(String orderId, String sku) {
        }

        CacheSupport<Order> orderCache = new CacheSupport<>(cacheManager) {
            @Override
            public Order load(String key) {
                return new Order("No10001", "sku10001");
            }

            @Override
            public String category() {
                return "order";
            }

            @Override
            public Long ttl() {
                return ttl;
            }
        };

        Order order = orderCache.get("No10001");
        Assertions.assertNull(order);

        ReflectionTestUtils.setField(LockSupport.class, "lock", new LocalLock());

        order = orderCache.safetyGet("No10001", 1000);
        Assertions.assertNotNull(order);
        Assertions.assertEquals("No10001", order.orderId);
    }

    @Test
    void HashMapCache_Single_Test() {
        // 因为是单例模式，所以cache取消泛型限制
        long ttl = 1000 * 60;
        HashMapCache<Object> cache = new HashMapCache<>(1000, ttl, "");
        CacheManager cacheManager = new HashMapCacheManager(cache, 1000, ttl);
        record User(String name, int age) {
        }
        CacheSupport<User> userCache = new CacheSupport<>(cacheManager) {
            @Override
            public User load(String key) {
                return new User("xiaoming", 18);
            }

            @Override
            public String category() {
                return "";
            }

            @Override
            public Long ttl() {
                return ttl;
            }
        };

        ReflectionTestUtils.setField(LockSupport.class, "lock", new LocalLock());

        User user = userCache.get("xiaoming");
        Assertions.assertNull(user);


        user = userCache.safetyGet("xiaoming", 1000);
        Assertions.assertNotNull(user);
        Assertions.assertEquals("xiaoming", user.name);

        record Order(String orderId, String sku) {
        }

        CacheSupport<Order> orderCache = new CacheSupport<>(cacheManager) {
            @Override
            public Order load(String key) {
                return new Order("No10001", "sku10001");
            }

            @Override
            public String category() {
                return "";
            }

            @Override
            public Long ttl() {
                return ttl;
            }
        };

        Order order = orderCache.get("No10001");
        Assertions.assertNull(order);

        order = orderCache.safetyGet("No10001", 1000);
        Assertions.assertNotNull(order);
        Assertions.assertEquals("No10001", order.orderId);

        order = orderCache.get("No10001");
        Assertions.assertNotNull(order);
        Assertions.assertEquals("No10001", order.orderId);
    }

    @Test
    void MultiHashMapCache_Prototype_Test() {
        record User(String name, int age) {
        }
        long ttl = 60 * 1000;
        MultiHashMapCache<Object> cache = new MultiHashMapCache<>(1000, ttl, null, "");
        CacheManager cacheManager = new MultiHashMapCacheManager(cache, null, 1000, ttl);
        CacheSupport<User> userCache = new CacheSupport<>(cacheManager) {
            @Override
            public User load(String key) {
                return new User("xiaoming", 18);
            }

            @Override
            public String category() {
                return "user";
            }

            @Override
            public Long ttl() {
                return ttl;
            }
        };

        User user = userCache.get("xiaoming");
        Assertions.assertNull(user);

        ReflectionTestUtils.setField(LockSupport.class, "lock", new LocalLock());

        user = userCache.safetyGet("xiaoming", 1000);
        Assertions.assertNotNull(user);
        Assertions.assertEquals("xiaoming", user.name);

        record Order(String orderId, String sku) {
        }

        CacheSupport<Order> orderCache = new CacheSupport<>(cacheManager) {
            @Override
            public Order load(String key) {
                return new Order("No10001", "sku10001");
            }

            @Override
            public String category() {
                return "order";
            }

            @Override
            public Long ttl() {
                return ttl;
            }
        };

        Order order = orderCache.get("No10001");
        Assertions.assertNull(order);

        ReflectionTestUtils.setField(LockSupport.class, "lock", new LocalLock());

        order = orderCache.safetyGet("No10001", 1000);
        Assertions.assertNotNull(order);
        Assertions.assertEquals("No10001", order.orderId);
    }

    @Test
    void MultiHashMapCache_Single_Test() {
        // 因为是单例模式，所以cache取消泛型限制
        long ttl = 60 * 1000;
        MultiHashMapCache<Object> cache = new MultiHashMapCache<>(1000, ttl, null, "");
        CacheManager cacheManager = new MultiHashMapCacheManager(cache, null, 1000, ttl);
        record User(String name, int age) {
        }
        CacheSupport<User> userCache = new CacheSupport<>(cacheManager) {
            @Override
            public User load(String key) {
                return new User("xiaoming", 18);
            }

            @Override
            public String category() {
                return "";
            }

            @Override
            public Long ttl() {
                return ttl;
            }
        };

        ReflectionTestUtils.setField(LockSupport.class, "lock", new LocalLock());

        User user = userCache.get("xiaoming");
        Assertions.assertNull(user);


        user = userCache.safetyGet("xiaoming", 1000);
        Assertions.assertNotNull(user);
        Assertions.assertEquals("xiaoming", user.name);

        record Order(String orderId, String sku) {
        }

        CacheSupport<Order> orderCache = new CacheSupport<>(cacheManager) {
            @Override
            public Order load(String key) {
                return new Order("No10001", "sku10001");
            }

            @Override
            public String category() {
                return "";
            }

            @Override
            public Long ttl() {
                return ttl;
            }
        };

        Order order = orderCache.get("No10001");
        Assertions.assertNull(order);

        order = orderCache.safetyGet("No10001", 1000);
        Assertions.assertNotNull(order);
        Assertions.assertEquals("No10001", order.orderId);

        order = orderCache.get("No10001");
        Assertions.assertNotNull(order);
        Assertions.assertEquals("No10001", order.orderId);
    }
}
