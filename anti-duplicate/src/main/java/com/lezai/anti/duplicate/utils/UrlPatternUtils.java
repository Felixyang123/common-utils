package com.lezai.anti.duplicate.utils;

import org.springframework.util.AntPathMatcher;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * URL 匹配器（支持 Ant 风格 + 正则）
 */
public class UrlPatternUtils {

    private static final AntPathMatcher ANT_MATCHER = new AntPathMatcher();

    /**
     * 匹配 URL（自动忽略查询参数）
     *
     * @param pattern    配置的模式（支持 Ant 风格或正则）
     * @param url 实际请求路径（如 /api/v1/order/123）
     * @return 是否匹配
     */
    public static boolean match(String pattern, String url) {
        // 1. 移除查询参数（如 ?id=123）
        String cleanUri = UriComponentsBuilder.fromUriString(url).build().getPath();

        return cleanUri != null && ANT_MATCHER.match(pattern, cleanUri);
    }
}