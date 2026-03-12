package com.lezai.idempotent.generator;

import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 基于 UserId 的幂等键生成器
 * 适用于需要根据用户ID进行幂等控制的场景
 */
public class UserIdKeyGenerator implements IdempotentKeyGenerator {
    
    private static final String USER_ID_HEADER = "X-User-Id";
    
    @Override
    public String generate(ProceedingJoinPoint joinPoint) {
        HttpServletRequest request = getCurrentRequest();
        if (request == null) {
            throw new IllegalStateException("No HTTP request context available");
        }
        
        String userId = request.getHeader(USER_ID_HEADER);
        if (userId == null || userId.isEmpty()) {
            // 尝试从 attribute 获取
            Object userIdAttr = request.getAttribute(USER_ID_HEADER);
            userId = userIdAttr != null ? userIdAttr.toString() : "anonymous";
        }
        
        return "user:" + userId;
    }
    
    private HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }
}
