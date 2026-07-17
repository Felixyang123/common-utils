package com.lezai.threadpool.utils;

import org.slf4j.MDC;

public final class LogContext {

    public static final String TRACE_ID = "traceId";
    public static final String APP_ID = "appId";
    public static final String POOL_NAME = "poolName";
    public static final String SERVER_NODE = "serverNode";

    private LogContext() {
    }

    public static void setTraceId(String traceId) {
        MDC.put(TRACE_ID, traceId);
    }

    public static void setAppId(String appId) {
        MDC.put(APP_ID, appId);
    }

    public static void setPoolName(String poolName) {
        MDC.put(POOL_NAME, poolName);
    }

    public static void setServerNode(String serverNode) {
        MDC.put(SERVER_NODE, serverNode);
    }

    public static void clear() {
        MDC.clear();
    }
}
