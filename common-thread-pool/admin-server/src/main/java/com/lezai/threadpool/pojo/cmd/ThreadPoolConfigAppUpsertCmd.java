package com.lezai.threadpool.pojo.cmd;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ThreadPoolConfigAppUpsertCmd {
    private String appId;

    private List<ThreadPoolConfigUpsertCmd> configs;
}
