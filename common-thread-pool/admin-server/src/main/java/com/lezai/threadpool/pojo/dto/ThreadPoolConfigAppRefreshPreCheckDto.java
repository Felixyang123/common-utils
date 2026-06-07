package com.lezai.threadpool.pojo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ThreadPoolConfigAppRefreshPreCheckDto {

    private String appId;

    private long version;

    private int configsCount;
}
