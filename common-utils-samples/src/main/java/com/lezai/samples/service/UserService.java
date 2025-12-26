package com.lezai.samples.service;

import com.lezai.samples.cache.UserCache;
import com.lezai.samples.cache.annotation.MethodCache;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserCache userCache;

    @MethodCache(key = "'user:' + #userId", ttl = 600000, category = "user")
    @SneakyThrows
    public UserCache.User getUserById(String userId) {
        // 模拟复杂业务逻辑或数据库查询
        System.out.println("query db：" + userId);
        Thread.sleep(1000);
        return userCache.load(userId);
    }

    public void updateUser(UserCache.User user) {
        userCache.set("user:" + user.getId(), user);
    }

}
