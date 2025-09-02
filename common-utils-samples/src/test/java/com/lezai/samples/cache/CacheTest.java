package com.lezai.samples.cache;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class CacheTest {

    @Autowired
    private UserCache userCache;

    @Test
    void userCacheTest() {
        UserCache.User user = userCache.get("1");
        Assertions.assertNull(user);
        user = userCache.safetyGet("1", 1000);
        Assertions.assertNotNull(user);
        Assertions.assertEquals("aaa", user.getName());
    }
}
