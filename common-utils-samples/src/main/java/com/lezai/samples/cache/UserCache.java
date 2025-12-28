package com.lezai.samples.cache;

import com.lezai.samples.cache.core.CacheAdapter;
import com.lezai.samples.cache.core.CacheManager;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class UserCache extends CacheAdapter<UserCache.User> {
    private final ConcurrentMap<String, User> users = new ConcurrentHashMap<>();

    public UserCache(CacheManager<Object> cacheManager) {
        super(cacheManager, "USER");
        users.put("1", new User("1","aaa"));
        users.put("2", new User("2","bbb"));
        users.put("3", new User("3","ccc"));
    }

    @Override
    public User load(String key) {
        return users.get(key);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class User {
        private String id;
        private String name;
    }
}
