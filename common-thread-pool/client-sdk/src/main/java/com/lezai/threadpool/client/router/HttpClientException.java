package com.lezai.threadpool.client.router;

import com.lezai.threadpool.client.ConfigServerClient.HttpStatusException;

/**
 * Wraps an HTTP 4xx error from the admin-server.
 *
 * <p>This is a {@link RuntimeException} (NOT an {@link java.io.IOException}) so that
 * {@link com.lezai.threadpool.client.router.FailoverRouter} — which catches only
 * {@code IOException} for failover — lets 4xx errors propagate immediately instead of
 * retrying on other nodes. Per ADR-0005, 4xx (auth failure, path not found, business
 * conflict) is a client/config problem, not a node failure, and must not trigger
 * failover or circuit-breaker trips.
 */
public class HttpClientException extends RuntimeException {

    private final HttpStatusException cause;

    public HttpClientException(HttpStatusException cause) {
        super("HTTP " + cause.getStatusCode() + " client error: " + cause.getMessage(), cause);
        this.cause = cause;
    }

    public int getStatusCode() {
        return cause.getStatusCode();
    }
}
