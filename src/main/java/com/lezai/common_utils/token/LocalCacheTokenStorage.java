package com.lezai.common_utils.token;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class LocalCacheTokenStorage implements TokenStorage<String, String> {
    private final ConcurrentMap<String, String> tokenCache = new ConcurrentHashMap<>();

    @Override
    public String getToken(String key) {
        return tokenCache.get(key);
    }

    @Override
    public boolean setToken(String key, String value) {
        return tokenCache.putIfAbsent(key, value) == null;
    }

    @Override
    public String deleteToken(String key) {
        return tokenCache.remove(key);
    }
}
