package com.lezai.threadpool.context;

/**
 * Current SDK client context holder (ThreadLocal pattern).
 *
 * <p>Holds the {@code appId} extracted from the {@code X-App-Id} header of Open API
 * requests. This lets audit events and service-layer code identify which SDK client
 * triggered an operation, distinct from admin-user-initiated actions (which use
 * {@link AdminUserContextHolder}).
 *
 * <p>Set by {@link com.lezai.threadpool.interceptor.ApiKeyAuthInterceptor#preHandle}
 * after successful API key validation, and cleared in {@code afterCompletion}.
 */
public final class ClientContextHolder {

    private static final ThreadLocal<String> CLIENT_ID = new ThreadLocal<>();

    private ClientContextHolder() {
    }

    public static void set(String appId) {
        CLIENT_ID.set(appId);
    }

    public static String get() {
        return CLIENT_ID.get();
    }

    public static void clear() {
        CLIENT_ID.remove();
    }
}
