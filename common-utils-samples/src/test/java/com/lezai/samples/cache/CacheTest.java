package com.lezai.samples.cache;

import com.lezai.samples.service.UserService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class CacheTest {

    @Autowired
    private UserCache userCache;
    @Autowired
    private UserService userService;

    @Test
    void userCacheTest() {
        UserCache.User user = userCache.get("1");
        Assertions.assertNull(user);
        user = userCache.safetyGet("1", 1000);
        Assertions.assertNotNull(user);
        Assertions.assertEquals("aaa", user.getName());
    }

    @Test
    void userServiceTest() {
        UserCache.User user = userService.getUserById("1");
        Assertions.assertNotNull(user);
        Assertions.assertEquals("1", user.getName());
    }
}
