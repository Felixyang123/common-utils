package com.lezai.anti.duplicate.interceptor;

import com.lezai.anti.duplicate.annotation.PreventDuplicateSubmit;
import com.lezai.anti.duplicate.config.AntiDupProperties;
import com.lezai.anti.duplicate.constants.Constant;
import com.lezai.anti.duplicate.resolver.DuplicateSubmitResultResolver;
import com.lezai.anti.duplicate.resolver.KeyResolver;
import com.lezai.anti.duplicate.strategy.DuplicateSubmitStrategy;
import com.lezai.anti.duplicate.utils.UrlPatternUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.http.HttpStatus;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Optional;

@RequiredArgsConstructor
public class DuplicateSubmitInterceptor implements HandlerInterceptor {

    private final AntiDupProperties properties;
    private final DuplicateSubmitStrategy strategy;
    private final KeyResolver keyResolver;
    private final DuplicateSubmitResultResolver resultResolver;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. 检查全局启用
        if (!properties.isEnabled()) {
            return true;
        }

        // 2. 自动跳过幂等方法
        if (Constant.IDEMPOTENT_METHODS.contains(request.getMethod())) {
            return true;
        }

        // 3. 在YAML配置且已经校验通过则放行
        if (preCheck(request.getRequestURI())) {
            return true;
        }

        // 4. 检查方法注解
        PreventDuplicateSubmit methodAnnotation = getMethodAnnotation(handler);

        PreventDuplicateSubmit classAnnotation = getClassAnnotation(handler);

        if (ObjectUtils.allNull(methodAnnotation, classAnnotation)) {
            return true;
        }

        // 5. 跳过防重校验
        if (skip(methodAnnotation, classAnnotation)) {
            return true;
        }

        // 6. 生成 Key
        String key = keyResolver.resolveKey(request);

        // 7. 尝试加锁
        int timeout = getTimeout(methodAnnotation, classAnnotation);
        if (!strategy.tryLock(key, timeout)) {
            response.setStatus(HttpStatus.OK.value());
            response.getWriter().write(resultResolver.resolve(request));
            return false;
        }
        return true;
    }

    private boolean preCheck(String url) {
        return properties.getUrlPatterns().stream().anyMatch(pattern ->
                UrlPatternUtils.match(pattern.getPath(), url));
    }

    private boolean skip(PreventDuplicateSubmit methodAnnotation, PreventDuplicateSubmit classAnnotation) {
        if (methodAnnotation == null) {
            return !classAnnotation.enabled();
        }
        return !methodAnnotation.enabled();
    }

    private PreventDuplicateSubmit getMethodAnnotation(Object handler) {
        return handler instanceof HandlerMethod handlerMethod ?
                handlerMethod.getMethodAnnotation(PreventDuplicateSubmit.class) : null;
    }

    private PreventDuplicateSubmit getClassAnnotation(Object handler) {
        return handler instanceof HandlerMethod handlerMethod ?
                handlerMethod.getBeanType().getAnnotation(PreventDuplicateSubmit.class) : null;
    }

    private int getTimeout(PreventDuplicateSubmit methodAnnotation, PreventDuplicateSubmit classAnnotation) {
        int timeout = Optional.ofNullable(methodAnnotation).map(PreventDuplicateSubmit::timeout)
                .orElseGet(() -> Optional.ofNullable(classAnnotation)
                        .map(PreventDuplicateSubmit::timeout).orElse(-1));
        return timeout > 0 ? timeout : properties.getDefaultTimeout();
    }
}