package com.lezai.threadpool.pojo.cmd;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 更新 API Key 请求
 */
@Data
public class UpdateApiKeyCmd {

    @Size(max = 128, message = "appName长度不能超过128")
    private String appName;

    private boolean enabled;

    @Size(max = 256, message = "description长度不能超过256")
    private String description;

    private LocalDateTime expireTime;
}
