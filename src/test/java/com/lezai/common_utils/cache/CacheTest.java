package com.lezai.common_utils.cache;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class CacheTest {

    @Autowired
    private UserCache userCache;

    @Autowired
    @Qualifier(value = "firstLevelCache")
    private MultiCache<Object> firstLevelCache;

    @Autowired
    private UserService userService;

    @Test
    void userCache_test() {
        UserCache.User user = userCache.safetyGet("3", System.currentTimeMillis() + 1000 * 60 * 30);
        System.out.println(user);
    }

    @Test
    void multiCache_test() {
        long ttl = System.currentTimeMillis() + 1000 * 60 * 30;
        Object user = firstLevelCache.loadAndCache("3", ttl, key -> new UserCache.User("aaa"));
        System.out.println(user);
    }

    @Test
    void userService_test() {
        UserCache.User user;
        user = userService.getUserById("4");
        System.out.println(user);

        UserCache.User user1 = userService.getUserById("4");
        System.out.println(user1==user);
    }
}
