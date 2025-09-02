package com.lezai.samples.service;

import com.lezai.samples.cache.UserCache;
import com.lezai.samples.cache.annotation.MethodCache;
import org.springframework.stereotype.Service;

@Service
public class UserService {
    
    @MethodCache(key = "'user:' + #userId", ttl = 60000, category = "user")
    public UserCache.User getUserById(String userId) {
        // 模拟复杂业务逻辑或数据库查询
        System.out.println("query db：" + userId);
        return new UserCache.User(userId);
    }

}
