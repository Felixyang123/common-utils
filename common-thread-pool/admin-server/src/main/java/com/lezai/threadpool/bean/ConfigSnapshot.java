package com.lezai.threadpool.bean;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 线程池配置版本快照
 * <p>
 * 历史快照链的单条记录，仅关心"某时间点配置长什么样"，
 * 不关心操作类型（操作类型归审计日志 operate_log）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfigSnapshot {
    private Long id;
    private String appId;
    private String poolName;
    private long version;
    private ThreadPoolConfig value;
    private String operator;
    private LocalDateTime createTime;
}
