package com.lezai.threadpool.controller.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChangeNicknameRequest {
    @NotBlank(message = "昵称不能为空")
    private String nickname;
}