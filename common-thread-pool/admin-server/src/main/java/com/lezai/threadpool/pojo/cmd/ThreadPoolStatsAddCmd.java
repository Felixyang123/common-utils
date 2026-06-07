package com.lezai.threadpool.pojo.cmd;

import com.lezai.threadpool.bean.ThreadPoolStats;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ThreadPoolStatsAddCmd {
    private String appId;

    private List<ThreadPoolStats> stats;
}
