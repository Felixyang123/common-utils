package com.lezai.threadpool.bean;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 线程池统计信息上报数据传输对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThreadPoolStatsReport implements Serializable {

    @Serial
    private static final long serialVersionUID = -596838692715005039L;
    /**
     * 应用ID
     */
    @NotBlank(message = "appId不能为空")
    @Size(max = 64, message = "appId长度不能超过64")
    private String appId;

    /**
     * 上报时间戳（毫秒）
     */
    private long reportTime;

    /**
     * 线程池统计信息列表
     */
    private List<ThreadPoolStats> statsList;
}
