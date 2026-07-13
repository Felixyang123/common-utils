package com.lezai.threadpool.pojo.response;

import lombok.Data;

/**
 * 重新生成 API Key 响应
 */
@Data
public class RegenerateApiKeyResponse {

    private String appId;

    private String apiKey; // 新的明文 API Key

}
