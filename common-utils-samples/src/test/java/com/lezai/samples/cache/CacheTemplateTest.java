package com.lezai.samples.cache;

import com.lezai.samples.cache.core.CacheTemplate;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class CacheTemplateTest {
    @Resource
    private CacheTemplate cacheTemplate;

    @Test
    @DisplayName("CacheTemplateTest")
    void cacheTemplateTest() {
        String color = cacheTemplate.get("COLOR", "1");
        Assertions.assertNull(color);
        cacheTemplate.set("COLOR", "1", "blank");
        color = cacheTemplate.get("COLOR", "1");
        Assertions.assertEquals("blank", color);

        UserCache.User user = cacheTemplate.get("USER", "1");
        Assertions.assertNull(user);
        cacheTemplate.set("USER", "1", new UserCache.User("1", "aaa"));
        user = cacheTemplate.get("USER", "1");
        Assertions.assertEquals("aaa", user.getName());

        cacheTemplate.remove("USER", "1");
        user = cacheTemplate.get("USER", "1");
        Assertions.assertNull(user);
    }
}
