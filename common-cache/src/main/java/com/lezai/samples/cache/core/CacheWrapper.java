package com.lezai.samples.cache.core;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Optional;

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

    public static <T> CacheWrapper<T> of(T data, Long ttl) {
        Long expireTime = Optional.ofNullable(ttl).map(t -> t + System.currentTimeMillis()).orElse(null);
        return CacheWrapper.<T>builder().data(data).expireTime(expireTime).build();
    }
}
