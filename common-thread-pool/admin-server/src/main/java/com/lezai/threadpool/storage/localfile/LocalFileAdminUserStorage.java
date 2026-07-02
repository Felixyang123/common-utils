package com.lezai.threadpool.storage.localfile;

import com.lezai.threadpool.bean.AdminUser;
import com.lezai.threadpool.storage.AdminUserStorage;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.Optional;

/**
 * 本地文件模式下的管理员账号存储 —— 单账号，直接从配置属性读取。
 * <p>
 * TODO: 后续如需支持多管理员账号，扩展为类似 {@link LocalFileApiKeyStorage} 的 JSON 文件存储。
 */
@Slf4j
public class LocalFileAdminUserStorage implements AdminUserStorage {

    private final String username;
    private final String passwordHash;

    public LocalFileAdminUserStorage(String username, String passwordHash) {
        this.username = username;
        this.passwordHash = passwordHash;
        log.info("Initialized LocalFileAdminUserStorage with single admin user: {}", username);
    }

    @Override
    public Optional<AdminUser> getByUsername(String queryUsername) {
        if (!StringUtils.equals(username, queryUsername)) {
            return Optional.empty();
        }
        return Optional.of(AdminUser.builder()
                .username(username)
                .passwordHash(passwordHash)
                .enabled(true)
                .build());
    }
}
