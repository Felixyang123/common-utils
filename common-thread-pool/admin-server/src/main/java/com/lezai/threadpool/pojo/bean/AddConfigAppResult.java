package com.lezai.threadpool.pojo.bean;

import com.lezai.threadpool.bean.ThreadPoolConfig;
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

    private String appId;

    private long version;
}
