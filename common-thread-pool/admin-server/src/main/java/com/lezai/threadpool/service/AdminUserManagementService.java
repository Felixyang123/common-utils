package com.lezai.threadpool.service;

import com.lezai.threadpool.bean.AdminUser;
import com.lezai.threadpool.bean.AdminUserContext;
import com.lezai.threadpool.bean.PageResult;
import com.lezai.threadpool.context.AdminUserContextHolder;
import com.lezai.threadpool.controller.dto.request.ChangeNicknameRequest;
import com.lezai.threadpool.controller.dto.request.ChangePasswordRequest;
import com.lezai.threadpool.controller.dto.request.CreateAdminUserRequest;
import com.lezai.threadpool.controller.dto.request.UpdateAdminUserRequest;
import com.lezai.threadpool.controller.dto.response.AdminUserResponse;
import com.lezai.threadpool.exception.*;
import com.lezai.threadpool.storage.AdminUserStorage;
import com.lezai.threadpool.utils.PasswordUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 管理员账号管理服务。
 * <p>
 * 包含保护规则：
 * <ul>
 *   <li>默认 admin 不可删除、不可禁用</li>
 *   <li>不可删除/禁用自己</li>
 *   <li>不可改自己角色</li>
 *   <li>创建/删除/更新管理员需要 SUPER_ADMIN 权限</li>
 *   <li>修改自己密码/昵称由用户自行操作</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserManagementService {

    private static final String DEFAULT_ADMIN = "admin";

    private final AdminUserStorage adminUserStorage;

    public PageResult<AdminUserResponse> page(int page, int pageSize) {
        requireSuperAdmin();
        PageResult<AdminUser> result = adminUserStorage.page(page, pageSize);
        return PageResult.of(result.getTotal(), result.getList().stream().map(this::toResponse).toList());
    }

    public AdminUserResponse createAdmin(CreateAdminUserRequest request) {
        requireSuperAdmin();

        String role = request.getRole() != null ? request.getRole() : "ADMIN";
        if (!"SUPER_ADMIN".equals(role) && !"ADMIN".equals(role)) {
            throw new ValidationException("Invalid role: " + role + " (must be SUPER_ADMIN or ADMIN)");
        }

        String nickname = request.getNickname() != null ? request.getNickname() : request.getUsername();

        AdminUser user = AdminUser.builder()
                .username(request.getUsername())
                .passwordHash(PasswordUtils.hash(request.getPassword()))
                .enabled(true)
                .role(role)
                .nickname(nickname)
                .passwordChangedAt(LocalDateTime.now())
                .build();

        try {
            adminUserStorage.save(user);
        } catch (DataIntegrityViolationException e) {
            throw new ResourceAlreadyExistsException("Admin user already exists: " + request.getUsername());
        }

        log.info("Admin user created: {} ({}) by {}", user.getUsername(), role, AdminUserContextHolder.get().getUsername());
        return toResponse(user);
    }

    public AdminUserResponse updateAdmin(String targetUsername, UpdateAdminUserRequest request) {
        requireSuperAdmin();
        AdminUserContext ctx = AdminUserContextHolder.get();

        AdminUser existing = adminUserStorage.getByUsername(targetUsername)
                .orElseThrow(() -> new ResourceNotFoundException("Admin user not found: " + targetUsername));

        // 保护规则：不可改自己角色
        if (request.getRole() != null && ctx.getUsername().equals(targetUsername)) {
            throw new AuthForbiddenException("You cannot change your own role");
        }

        // 保护规则：默认 admin 角色不可更改
        if (DEFAULT_ADMIN.equals(targetUsername) && request.getRole() != null) {
            throw new AuthForbiddenException("Default admin user role cannot be changed");
        }

        // 保护规则：默认 admin 不可禁用
        if (DEFAULT_ADMIN.equals(targetUsername) && Boolean.FALSE.equals(request.getEnabled())) {
            throw new AuthForbiddenException("Default admin user cannot be disabled");
        }

        AdminUser.AdminUserBuilder builder = AdminUser.builder()
                .id(existing.getId()).username(targetUsername);

        if (request.getPassword() != null) {
            builder.passwordHash(PasswordUtils.hash(request.getPassword()));
            builder.passwordChangedAt(LocalDateTime.now());
        } else {
            builder.passwordHash(existing.getPasswordHash());
            builder.passwordChangedAt(existing.getPasswordChangedAt());
        }

        builder.enabled(request.getEnabled() != null ? request.getEnabled() : existing.isEnabled());
        builder.role(request.getRole() != null ? request.getRole() : existing.getRole());
        builder.nickname(request.getNickname() != null ? request.getNickname() : existing.getNickname());

        AdminUser adminUser = builder.build();
        adminUserStorage.update(adminUser);
        log.info("Admin user updated: {} by {}", targetUsername, ctx.getUsername());
        return toResponse(adminUser);
    }

    public void deleteAdmin(String targetUsername) {
        requireSuperAdmin();
        AdminUserContext ctx = AdminUserContextHolder.get();

        // 保护规则：默认 admin 不可删除
        if (DEFAULT_ADMIN.equals(targetUsername)) {
            throw new AuthForbiddenException("Default admin user cannot be deleted");
        }

        // 保护规则：不可删除自己
        if (ctx.getUsername().equals(targetUsername)) {
            throw new AuthForbiddenException("You cannot delete yourself");
        }

        if (!adminUserStorage.existsByUsername(targetUsername)) {
            throw new ResourceNotFoundException("Admin user not found: " + targetUsername);
        }

        adminUserStorage.deleteByUsername(targetUsername);
        log.info("Admin user deleted: {} by {}", targetUsername, ctx.getUsername());
    }

    public void changeMyPassword(ChangePasswordRequest request) {
        AdminUserContext ctx = AdminUserContextHolder.get();

        AdminUser existing = adminUserStorage.getByUsername(ctx.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + ctx.getUsername()));

        if (!PasswordUtils.matches(request.getOldPassword(), existing.getPasswordHash())) {
            throw new AuthenticationException("Old password is incorrect");
        }

        AdminUser updated = AdminUser.builder()
                .id(existing.getId())
                .username(existing.getUsername())
                .passwordHash(PasswordUtils.hash(request.getNewPassword()))
                .enabled(existing.isEnabled())
                .role(existing.getRole())
                .nickname(existing.getNickname())
                .passwordChangedAt(LocalDateTime.now())
                .build();
        adminUserStorage.update(updated);

        log.info("Password changed for user: {}", ctx.getUsername());
    }

    public void changeMyNickname(ChangeNicknameRequest request) {
        AdminUserContext ctx = AdminUserContextHolder.get();

        AdminUser existing = adminUserStorage.getByUsername(ctx.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + ctx.getUsername()));

        AdminUser updated = AdminUser.builder()
                .id(existing.getId())
                .username(existing.getUsername())
                .passwordHash(existing.getPasswordHash())
                .enabled(existing.isEnabled())
                .role(existing.getRole())
                .nickname(request.getNickname())
                .passwordChangedAt(existing.getPasswordChangedAt())
                .build();
        adminUserStorage.update(updated);

        log.info("Nickname changed for user: {} -> {}", ctx.getUsername(), request.getNickname());
    }

    private void requireSuperAdmin() {
        AdminUserContext ctx = AdminUserContextHolder.get();
        if (ctx == null || !ctx.isSuperAdmin()) {
            throw new AuthenticationException("Only SUPER_ADMIN can perform this operation");
        }
    }

    private AdminUserResponse toResponse(AdminUser user) {
        return AdminUserResponse.builder()
                .username(user.getUsername())
                .nickname(user.getNickname())
                .role(user.getRole())
                .enabled(user.isEnabled())
                .build();
    }
}