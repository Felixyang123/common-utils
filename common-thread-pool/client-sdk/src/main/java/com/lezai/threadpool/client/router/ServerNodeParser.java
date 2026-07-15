package com.lezai.threadpool.client.router;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

public final class ServerNodeParser {

    private ServerNodeParser() {
    }

    public static List<ServerNode> parse(String serverUrl) {
        if (serverUrl == null || serverUrl.isBlank()) {
            return List.of();
        }
        List<ServerNode> nodes = new ArrayList<>();
        for (String raw : serverUrl.split(",")) {
            String token = raw.trim();
            if (token.isBlank()) {
                continue;
            }
            nodes.add(parseOne(token));
        }
        return nodes;
    }

    private static ServerNode parseOne(String token) {
        int weight = 1;
        String url = token;

        if (!isValidUri(token)) {
            int lastColon = token.lastIndexOf(':');
            if (lastColon >= 0) {
                String suffix = token.substring(lastColon + 1);
                if (suffix.matches("\\d+")) {
                    String candidate = token.substring(0, lastColon);
                    if (isValidUri(candidate)) {
                        weight = Integer.parseInt(suffix);
                        url = candidate;
                    }
                }
            }
        }

        if (!isValidUri(url)) {
            throw new IllegalArgumentException("Invalid admin-server URL: " + token);
        }
        return new ServerNode(url, weight);
    }

    private static boolean isValidUri(String value) {
        try {
            URI uri = URI.create(value);
            return uri.getScheme() != null && uri.getHost() != null;
        } catch (Exception e) {
            return false;
        }
    }
}