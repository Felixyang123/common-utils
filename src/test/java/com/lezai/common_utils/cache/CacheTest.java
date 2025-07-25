package com.lezai.common_utils.cache;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class CacheTest {

    @Autowired
    private UserCache userCache;

    @Test
    void userCache_test() {
        UserCache.User user = userCache.safetyGet("3", System.currentTimeMillis() + 1000 * 60 * 30);
        System.out.println(user);
    }
}
