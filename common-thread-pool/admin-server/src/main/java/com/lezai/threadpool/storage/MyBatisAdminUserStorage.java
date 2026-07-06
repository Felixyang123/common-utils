package com.lezai.threadpool.storage;

import com.lezai.threadpool.bean.AdminUser;
import com.lezai.threadpool.dao.entity.AdminUserEntity;
import com.lezai.threadpool.dao.rep.AdminUserRep;
import com.lezai.threadpool.utils.PasswordUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 基于 MyBatis 的管理员账号存储。
 * <p>
 * 在 local / db 两个 profile 下共用。
 * 首次启动若表为空，自动创建配置提供的默认管理员账号（SUPER_ADMIN）。
 */
@Slf4j
@RequiredArgsConstructor
public class MyBatisAdminUserStorage implements AdminUserStorage {

    private final AdminUserRep adminUserRep;

    @Override
    public Optional<AdminUser> getByUsername(String username) {
        return adminUserRep.findByUsername(username).map(this::toUser);
    }

    @Override
    public List<AdminUser> listAll() {
        return adminUserRep.list().stream().map(this::toUser).toList();
    }

    @Override
    public void save(AdminUser adminUser) {
        AdminUserEntity entity = toEntity(adminUser);
        if (entity.getPasswordChangedAt() == null) {
            entity.setPasswordChangedAt(LocalDateTime.now());
        }
        adminUserRep.save(entity);
    }

    @Override
    public void update(AdminUser adminUser) {
        AdminUserEntity entity = toEntity(adminUser);
        adminUserRep.updateById(entity);
    }

    @Override
    public boolean deleteByUsername(String username) {
        return adminUserRep.deleteByUsername(username);
    }

    @Override
    public boolean existsByUsername(String username) {
        return adminUserRep.existsByUsername(username);
    }

    /**
     * 首次启动初始化：表为空时创建配置提供的默认管理员账号（SUPER_ADMIN）
     */
    public void ensureDefaultUser(String defaultUsername, String defaultPassword) {
        if (adminUserRep.existsAny()) {
            return;
        }
        AdminUserEntity entity = AdminUserEntity.builder()
                .username(defaultUsername)
                .passwordHash(PasswordUtils.hash(defaultPassword))
                .enabled(true)
                .role("SUPER_ADMIN")
                .nickname(defaultUsername)
                .passwordChangedAt(LocalDateTime.now())
                .build();
        adminUserRep.save(entity);
        log.info("Created default admin user: {} (SUPER_ADMIN)", defaultUsername);
    }

    private AdminUser toUser(AdminUserEntity entity) {
        return AdminUser.builder()
                .username(entity.getUsername())
                .passwordHash(entity.getPasswordHash())
                .enabled(Boolean.TRUE.equals(entity.getEnabled()))
                .role(entity.getRole() != null ? entity.getRole() : "ADMIN")
                .nickname(entity.getNickname() != null ? entity.getNickname() : entity.getUsername())
                .passwordChangedAt(entity.getPasswordChangedAt())
                .build();
    }

    private AdminUserEntity toEntity(AdminUser user) {
        return AdminUserEntity.builder()
                .username(user.getUsername())
                .passwordHash(user.getPasswordHash())
                .enabled(user.isEnabled())
                .role(user.getRole())
                .nickname(user.getNickname())
                .passwordChangedAt(user.getPasswordChangedAt())
                .build();
    }
}