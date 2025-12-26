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
        users.put("1", new User("1","aaa"));
        users.put("2", new User("2","bbb"));
        users.put("3", new User("3","ccc"));
    }

    @Override
    public User load(String key) {
        return users.get(key);
    }

    @Override
    public String category() {
        return "user";
    }

    @Override
    public Long ttl() {
        return 200000L;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class User {
        private String id;
        private String name;
    }
}
