package com.lezai.threadpool.pojo.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateAppRequest {

    @NotBlank(message = "appId不能为空")
    private String appId;

    private String appName;

    private String description;
}
