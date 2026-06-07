package com.lezai.threadpool.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum StorageType {
    API_KEY("API Key"),
    API_KEY_HISTORY("API Key History"),
    THREAD_POOL_CONFIG("Thread Pool Config"),
    THREAD_POOL_CONFIG_HISTORY("Thread Pool Config History"),
    THREAD_POOL_STATS("Thread Pool Stats");


    private final String description;
}
