package com.lezai.threadpool.storage;

import com.lezai.threadpool.pojo.bean.AdminUser;
import com.lezai.threadpool.pojo.bean.PageResult;

import java.util.Optional;

public interface AdminUserStorage {

    Optional<AdminUser> getByUsername(String username);

    PageResult<AdminUser> page(int page, int pageSize);

    void save(AdminUser adminUser);

    void update(AdminUser adminUser);

    boolean deleteByUsername(String username);

    boolean existsByUsername(String username);
}


