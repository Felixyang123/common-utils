package com.lezai.threadpool.storage;

import com.lezai.threadpool.bean.AdminUser;
import com.lezai.threadpool.dao.entity.AdminUserEntity;
import com.lezai.threadpool.dao.rep.AdminUserRep;
import com.lezai.threadpool.utils.PasswordUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

/**
 * 基于 MyBatis 的管理员账号存储。
 * <p>
 * 在 local / db 两个 profile 下共用——持久化层是唯一的不变量，
 * 仅缓存层（{@link CacheService}）随 profile 切换。
 * <p>
 * 登录请求频率低、账号数量少，不需要缓存层（直通查询 MyBatis 即可）。
 * 首次启动若表为空，自动创建配置提供的默认管理员账号。
 */
@Slf4j
@RequiredArgsConstructor
public class MyBatisAdminUserStorage implements AdminUserStorage {

    private final AdminUserRep adminUserRep;

    @Override
    public Optional<AdminUser> getByUsername(String username) {
        return adminUserRep.findByUsername(username).map(entity -> AdminUser.builder()
                .username(entity.getUsername())
                .passwordHash(entity.getPasswordHash())
                .enabled(Boolean.TRUE.equals(entity.getEnabled()))
                .build());
    }

    /**
     * 首次启动初始化：表为空时创建配置提供的默认管理员账号
     */
    public void ensureDefaultUser(String defaultUsername, String defaultPassword) {
        if (adminUserRep.existsAny()) {
            return;
        }
        AdminUserEntity entity = AdminUserEntity.builder()
                .username(defaultUsername)
                .passwordHash(PasswordUtils.hash(defaultPassword))
                .enabled(true)
                .build();
        adminUserRep.save(entity);
        log.info("Created default admin user: {}", defaultUsername);
    }
}