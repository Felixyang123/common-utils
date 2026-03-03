package com.lezai.anti.duplicate.resolver;

import jakarta.servlet.http.HttpServletRequest;

public class DefaultDuplicateSubmitResultResolver implements DuplicateSubmitResultResolver {
    @Override
    public String resolve(HttpServletRequest request) {
        return "Duplicate submit";
    }
}
