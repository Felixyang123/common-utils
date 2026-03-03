package com.lezai.anti.duplicate.interceptor;

import com.lezai.anti.duplicate.config.AntiDupProperties;
import com.lezai.anti.duplicate.constants.Constant;
import com.lezai.anti.duplicate.resolver.DuplicateSubmitResultResolver;
import com.lezai.anti.duplicate.resolver.KeyResolver;
import com.lezai.anti.duplicate.strategy.DuplicateSubmitStrategy;
import com.lezai.anti.duplicate.utils.UrlPatternUtils;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;

@RequiredArgsConstructor
public class DuplicateSubmitFilter implements Filter {
    private final AntiDupProperties properties;
    private final DuplicateSubmitStrategy strategy;
    private final KeyResolver keyResolver;
    private final DuplicateSubmitResultResolver resultResolver;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        ContentCachingRequestWrapper requestWrapper = new ContentCachingRequestWrapper(httpRequest);
        // 1. 检查全局是否启用
        if (!properties.isEnabled()) {
            chain.doFilter(requestWrapper, response);
            return;
        }

        // 2. 自动跳过幂等方法
        if (Constant.IDEMPOTENT_METHODS.contains(httpRequest.getMethod())) {
            chain.doFilter(requestWrapper, response);
            return;
        }

        // 3. 尝试匹配 URL
        AntiDupProperties.PatternConfig pattern = findPattern(httpRequest.getRequestURI());
        if (pattern == null || !pattern.isEnabled()) {
            chain.doFilter(requestWrapper, response);
            return;
        }

        // 4. 生成 Key
        String key = keyResolver.resolveKey(requestWrapper);

        // 5. 尝试加锁
        int timeout = pattern.getTimeout() > 0 ? pattern.getTimeout() : properties.getDefaultTimeout();
        if (!strategy.tryLock(key, timeout)) {
            httpResponse.setStatus(HttpStatus.OK.value());
            httpResponse.getWriter().write(resultResolver.resolve(requestWrapper));
            return;
        }

        chain.doFilter(requestWrapper, response);
    }

    private AntiDupProperties.PatternConfig findPattern(String url) {
        return properties.getUrlPatterns().stream().filter(pattern ->
                UrlPatternUtils.match(pattern.getPath(), url)).findFirst().orElse(null);
    }
}