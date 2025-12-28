package com.lezai.samples.cache.core;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CacheWrapper<T> {

    private T data;

    private Long expireTime;

    public Boolean expired() {
        return this.expireTime != null && this.expireTime <= System.currentTimeMillis();
    }
}
