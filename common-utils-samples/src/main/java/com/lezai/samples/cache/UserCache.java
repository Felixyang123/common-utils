package com.lezai.samples.cache;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class UserCache extends CacheSupport<UserCache.User> {
    private final ConcurrentMap<String, User> users = new ConcurrentHashMap<>();

    public UserCache(CacheManager<Object> cacheManager) {
        super(cacheManager);
        users.put("1", new User("aaa"));
        users.put("2", new User("bbb"));
    }

    @Override
    public User load(String key) {
        return users.get(key);
    }

    @Override
    public String category() {
        return "user";
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class User {
        private String name;
    }
}
