package com.lezai.anti.duplicate.resolver;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Optional;

public class AuthTokenResolver implements TokenResolver {
    @Override
    public String resolveToken(HttpServletRequest request) {
        return Optional.ofNullable(request.getHeader("Authorization")).orElse("");
    }
}
