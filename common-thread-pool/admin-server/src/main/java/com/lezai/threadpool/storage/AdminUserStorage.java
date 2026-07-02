package com.lezai.threadpool.storage;

import com.lezai.threadpool.bean.AdminUser;
import com.lezai.threadpool.utils.PasswordUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * 管理员账号存储接口
 * local-file 模式：单账号从配置属性读取；redis-mysql 模式：查 admin_user 表
 */
public interface AdminUserStorage {

    Logger log = LoggerFactory.getLogger(AdminUserStorage.class);

    /**
     * 根据用户名获取管理员账号
     *
     * @param username 用户名
     * @return Optional 包装的 AdminUser
     */
    Optional<AdminUser> getByUsername(String username);

    /**
     * 校验用户名密码
     *
     * @param username 用户名
     * @param password 明文密码
     * @return true 如果校验通过
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
}
