package com.lezai.threadpool.controller.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 创建 API Key 请求
 */
@Data
public class CreateApiKeyRequest {

    @NotBlank(message = "appId不能为空")
    @Size(max = 64, message = "appId长度不能超过64")
    private String appId;

    @NotBlank(message = "appName不能为空")
    @Size(max = 128, message = "appName长度不能超过128")
    private String appName;

    @Size(max = 256, message = "description长度不能超过256")
    private String description;

    private LocalDateTime expireTime;
}
