package com.lezai.threadpool.controller.dto.request;

import lombok.Data;

@Data
public class UpdateAdminUserRequest {
    private String password;
    private String nickname;
    private Boolean enabled;
    private String role;
}