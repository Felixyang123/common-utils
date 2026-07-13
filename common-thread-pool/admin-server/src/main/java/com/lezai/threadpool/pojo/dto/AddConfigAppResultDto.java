package com.lezai.threadpool.pojo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddConfigAppResultDto {
    private List<ThreadPoolConfigDto> addedConfigs;

    private List<ThreadPoolConfigDto> existConfigs;

    private String appId;

    private long version;
}
