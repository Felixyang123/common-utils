package com.lezai.threadpool.storage;

import com.lezai.threadpool.bean.AdminUser;
import com.lezai.threadpool.utils.PasswordUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * 管理员账号存储接口
 */
public interface AdminUserStorage {

    Logger log = LoggerFactory.getLogger(AdminUserStorage.class);

    Optional<AdminUser> getByUsername(String username);

    /**
     * 校验用户名密码
     */
    default boolean validateCredentials(String username, String password) {
        if (StringUtils.isAnyBlank(username, password)) {
            return false;
        }
        Optional<AdminUser> userOptional = getByUsername(username);
        if (userOptional.isEmpty()) {
            log.warn("Admin user not found: {}", username);
            return false;
        }
        AdminUser user = userOptional.get();
        if (!user.isEnabled()) {
            log.warn("Admin user is disabled: {}", username);
            return false;
        }
        boolean valid = PasswordUtils.matches(password, user.getPasswordHash());
        if (!valid) {
            log.warn("Admin password validation failed for user: {}", username);
        }
        return valid;
    }

    // ==================== CRUD ====================

    List<AdminUser> listAll();

    void save(AdminUser adminUser);

    void update(AdminUser adminUser);

    boolean deleteByUsername(String username);

    boolean existsByUsername(String username);
}