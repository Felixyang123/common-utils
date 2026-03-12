package com.lezai.idempotent.generator;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.aspectj.lang.ProceedingJoinPoint;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 基于 SessionId 的幂等键生成器
 * 适用于需要根据会话进行幂等控制的场景
 */
public class SessionIdKeyGenerator implements IdempotentKeyGenerator {
    
    @Override
    public String generate(ProceedingJoinPoint joinPoint) {
        HttpServletRequest request = getCurrentRequest();
        if (request == null) {
            throw new IllegalStateException("No HTTP request context available");
        }
        
        HttpSession session = request.getSession(false);
        if (session == null) {
            throw new IllegalStateException("No session available");
        }
        
        return "session:" + session.getId();
    }
    
    private HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }
}
