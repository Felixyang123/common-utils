package com.lezai.samples.cache;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CacheWrapper<T> {
    private String key;

    private String category;

    private T data;

    private Long expireTime;

    public Boolean expired() {
        return expireTime < System.currentTimeMillis();
    }
}
