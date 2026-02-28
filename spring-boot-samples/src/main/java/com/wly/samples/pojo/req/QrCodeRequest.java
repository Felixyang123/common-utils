package com.wly.samples.pojo.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;


@Data
public class QrCodeRequest {
    
    @NotBlank(message = "内容不能为空")
    @Size(max = 500, message = "内容长度不能超过500个字符")
    private String content;
    
    private Integer width = 300;
    private Integer height = 300;
    private Integer margin = 1;
    private String format = "png"; // png, jpg, gif
    
    // 可选参数
    private String logoUrl;
    private String foregroundColor = "#000000";
    private String backgroundColor = "#FFFFFF";
}