package com.lezai.anti.duplicate.resolver;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
public class KeyResolver {

    private final TokenResolver tokenResolver;

    public String resolveKey(HttpServletRequest request) {
        String token = tokenResolver.resolveToken(request);
        String url = request.getRequestURL().toString();
        String params = getParameters(request);
        String body = getBody(request);
        return DigestUtils.sha1Hex(token + url + params + body);
    }

    private String getParameters(HttpServletRequest request) {
        // 获取查询参数
        Map<String, String> params = request.getParameterMap().entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> String.join(",", e.getValue())
                ));
        return params.isEmpty() ? "" : params.toString();
    }

    private String getBody(HttpServletRequest request) {
        if (request instanceof ContentCachingRequestWrapper requestWrapper) {
            byte[] buf = requestWrapper.getContentAsByteArray();
            if (buf.length > 0) {
                return new String(buf, StandardCharsets.UTF_8);
            }
        }
        return "";
    }
}