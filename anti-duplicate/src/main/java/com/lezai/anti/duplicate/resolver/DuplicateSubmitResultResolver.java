package com.lezai.anti.duplicate.resolver;

import jakarta.servlet.http.HttpServletRequest;

public interface DuplicateSubmitResultResolver {

    String resolve(HttpServletRequest request);
}
