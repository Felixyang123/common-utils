package com.lezai.threadpool.client.router;

import java.io.IOException;

public class NodeUnavailableException extends IOException {

    public NodeUnavailableException(String message) {
        super(message);
    }
}
