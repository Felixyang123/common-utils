package com.lezai.threadpool.controller.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserResponse {
    private String username;
    private String nickname;
    private String role;
    private boolean enabled;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}