package com.lezai.threadpool.bean;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddConfigAppResult {
    private List<ThreadPoolConfig> addedConfigs;

    private List<ThreadPoolConfig> existConfigs;

    private List<ThreadPoolConfig> retiredConfigs;

    private String appId;

    private long version;
}
