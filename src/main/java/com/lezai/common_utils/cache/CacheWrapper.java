package com.lezai.common_utils.cache;

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

//    @JsonIgnore
    public Boolean expired() {
        return expireTime < System.currentTimeMillis();
    }
}
