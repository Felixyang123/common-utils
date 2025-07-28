package com.lezai.common_utils.cache;

import com.lezai.common_utils.cache.annotation.MethodCache;
import org.springframework.stereotype.Service;

@Service
public class UserService {
    
    @MethodCache(key = "'user:' + #userId", ttl = 60000)
    public UserCache.User getUserById(String userId) {
        // 模拟复杂业务逻辑或数据库查询
        System.out.println("query db：" + userId);
        return new UserCache.User(userId);
    }

}
