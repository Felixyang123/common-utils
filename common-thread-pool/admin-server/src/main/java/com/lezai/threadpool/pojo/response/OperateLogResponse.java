package com.lezai.threadpool.pojo.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OperateLogResponse {
    private Long id;
    private String bizId;
    private String bizType;
    private String operateType;
    private String content;
    private String operator;
    private LocalDateTime createTime;
}