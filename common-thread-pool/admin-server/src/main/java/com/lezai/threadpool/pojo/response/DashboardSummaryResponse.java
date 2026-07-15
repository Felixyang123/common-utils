package com.lezai.threadpool.pojo.response;

import com.lezai.threadpool.pojo.bean.PoolAlert;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DashboardSummaryResponse {
    private int appCount;
    private int apiKeyCount;
    private int configCount;
    private int alertCount;
    private boolean hasMore;
    private List<PoolAlert> alerts;
    private List<OperateLogResponse> recentLogs;
}
