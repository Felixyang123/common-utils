package com.lezai.idempotent.generator;

import com.lezai.idempotent.exception.IdempotentException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 基于 UserId 的幂等键生成器。
 * 优先从认证上下文（request attribute）获取用户标识，
 * 仅在 attribute 缺失时 fallback 到 HTTP Header。
 */
@Slf4j
public class UserIdKeyGenerator implements IdempotentKeyGenerator {

    private static final String USER_ID_HEADER = "X-User-Id";
    /** 由上游认证过滤器/网关写入的属性名 */
    private static final String AUTH_USER_ATTR = "X-User-Id";

    private final boolean rejectAnonymous;

    /**
     * @param rejectAnonymous true: 未认证用户抛异常；false: 未认证用户用共享匿名key
     */
    public UserIdKeyGenerator(boolean rejectAnonymous) {
        this.rejectAnonymous = rejectAnonymous;
    }

    /** 默认拒绝匿名（安全优先） */
    public UserIdKeyGenerator() {
        this(true);
    }

    @Override
    public String generate(ProceedingJoinPoint joinPoint) {
        HttpServletRequest request = getCurrentRequest();
        if (request == null) {
            throw new IllegalStateException("No HTTP request context available");
        }

        // 1. 优先：认证上下文（由认证过滤器/网关写入）
        Object authAttr = request.getAttribute(AUTH_USER_ATTR);
        if (authAttr != null && !authAttr.toString().isEmpty()) {
            return "user:" + authAttr;
        }

        // 2. 兜底：HTTP Header（客户端可伪造，仅作兼容）
        String headerVal = request.getHeader(USER_ID_HEADER);
        if (headerVal != null && !headerVal.isEmpty()) {
            log.warn("UserId 取自 HTTP Header，存在伪造风险，建议由认证过滤器写入 request attribute");
            return "user:" + headerVal;
        }

        // 3. 匿名处理
        if (rejectAnonymous) {
            throw new IdempotentException(
                    "无法获取用户标识：请在 request attribute 或 Header 中设置 X-User-Id，"
                    + "或配置 idempotent.security.anonymous-strategy=ALLOW");
        }
        log.warn("未获取到用户标识，使用 anonymous 共享key（不同匿名用户的相同请求会被误判为重复）");
        return "user:anonymous";
    }

    private HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }
}
