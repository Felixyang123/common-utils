package com.lezai.threadpool.dao.rep;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lezai.threadpool.dao.entity.AdminUserEntity;
import com.lezai.threadpool.dao.mapper.AdminUserMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class AdminUserRep extends ServiceImpl<AdminUserMapper, AdminUserEntity> {

    public Optional<AdminUserEntity> findByUsername(String username) {
        return Optional.ofNullable(getOne(Wrappers.<AdminUserEntity>lambdaQuery()
                .eq(AdminUserEntity::getUsername, username)));
    }

    public Page<AdminUserEntity> page(int pageNum, int pageSize) {
        return page(new Page<>(pageNum, pageSize), Wrappers.<AdminUserEntity>lambdaQuery().orderByDesc(AdminUserEntity::getCreateTime));
    }

    public boolean existsAny() {
        return count() > 0;
    }

    public boolean existsByUsername(String username) {
        return count(Wrappers.<AdminUserEntity>lambdaQuery()
                .eq(AdminUserEntity::getUsername, username)) > 0;
    }

    public boolean deleteByUsername(String username) {
        return remove(Wrappers.<AdminUserEntity>lambdaQuery()
                .eq(AdminUserEntity::getUsername, username));
    }
}