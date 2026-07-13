package com.lezai.threadpool.controller;

import com.lezai.threadpool.bean.ApiResponse;
import com.lezai.threadpool.pojo.bean.PageResult;
import com.lezai.threadpool.pojo.request.ChangeNicknameRequest;
import com.lezai.threadpool.pojo.request.ChangePasswordRequest;
import com.lezai.threadpool.pojo.request.CreateAdminUserRequest;
import com.lezai.threadpool.pojo.request.UpdateAdminUserRequest;
import com.lezai.threadpool.pojo.response.AdminUserResponse;
import com.lezai.threadpool.service.AdminUserManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 管理员账号管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/admin-users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserManagementService adminUserManagementService;

    /**
     * 分页列出管理员（�?SUPER_ADMIN�?
     */
    @GetMapping
    public ApiResponse<PageResult<AdminUserResponse>> pageAdminUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.success(adminUserManagementService.page(page, pageSize));
    }

    /**
     * 创建管理员（�?SUPER_ADMIN�?
     */
    @PostMapping
    public ApiResponse<AdminUserResponse> createAdmin(@Valid @RequestBody CreateAdminUserRequest request) {
        return ApiResponse.success(adminUserManagementService.createAdmin(request));
    }

    /**
     * 更新管理员（�?SUPER_ADMIN�?
     */
    @PutMapping("/{username}")
    public ApiResponse<AdminUserResponse> updateAdmin(
            @PathVariable String username,
            @Valid @RequestBody UpdateAdminUserRequest request) {
        return ApiResponse.success(adminUserManagementService.updateAdmin(username, request));
    }

    /**
     * 删除管理员（�?SUPER_ADMIN，不可删除自�?默认 admin�?
     */
    @DeleteMapping("/{username}")
    public ApiResponse<Void> deleteAdmin(@PathVariable String username) {
        adminUserManagementService.deleteAdmin(username);
        return ApiResponse.success();
    }

    /**
     * 修改自己密码（任何角色）
     */
    @PutMapping("/me/password")
    public ApiResponse<Void> changeMyPassword(@Valid @RequestBody ChangePasswordRequest request) {
        adminUserManagementService.changeMyPassword(request);
        return ApiResponse.success();
    }

    /**
     * 修改自己昵称（任何角色）
     */
    @PutMapping("/me/nickname")
    public ApiResponse<Void> changeMyNickname(@Valid @RequestBody ChangeNicknameRequest request) {
        adminUserManagementService.changeMyNickname(request);
        return ApiResponse.success();
    }
}