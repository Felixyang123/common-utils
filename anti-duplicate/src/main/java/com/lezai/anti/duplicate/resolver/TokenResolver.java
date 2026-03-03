package com.lezai.anti.duplicate.resolver;

import jakarta.servlet.http.HttpServletRequest;

public interface TokenResolver {
    String resolveToken(HttpServletRequest request);
}
