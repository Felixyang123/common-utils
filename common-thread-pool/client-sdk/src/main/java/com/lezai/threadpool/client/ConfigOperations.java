package com.lezai.threadpool.client;

import com.lezai.threadpool.bean.AddConfigAppResult;
import com.lezai.threadpool.bean.ConfigChangeNotification;
import com.lezai.threadpool.bean.ThreadPoolConfig;
import com.lezai.threadpool.bean.ThreadPoolConfigResp;
import com.lezai.threadpool.bean.ThreadPoolStatsReport;

import java.io.IOException;
import java.util.List;

public interface ConfigOperations {
    ConfigChangeNotification subscribe(long version, long timeoutMs) throws IOException;
    ThreadPoolConfigResp pullConfigs(Long version) throws IOException;
    AddConfigAppResult registerConfig(ThreadPoolConfig config) throws IOException;
    AddConfigAppResult registerConfigs(List<ThreadPoolConfig> configs) throws IOException;
    void reportStats(ThreadPoolStatsReport report) throws IOException;
    void release();
}