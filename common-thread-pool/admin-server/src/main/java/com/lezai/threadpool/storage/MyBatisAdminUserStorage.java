package com.lezai.threadpool.storage;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lezai.threadpool.dao.entity.AdminUserEntity;
import com.lezai.threadpool.dao.rep.AdminUserRep;
import com.lezai.threadpool.pojo.bean.AdminUser;
import com.lezai.threadpool.pojo.bean.PageResult;
import com.lezai.threadpool.utils.PasswordUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 鍩轰簬 MyBatis 鐨勭�＄悊鍛樿处鍙峰瓨鍌ㄣ�?
 * <p>
 * 鍦?local / db 涓や釜 profile 涓嬪叡鐢ㄣ�?
 * 棣栨�″惎鍔ㄨ嫢琛ㄤ负绌猴紝鑷�鍔ㄥ垱寤洪厤缃�鎻愪緵鐨勯粯璁ょ�＄悊鍛樿处鍙凤紙SUPER_ADMIN锛夈�?
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
    public PageResult<AdminUser> page(int page, int pageSize) {
        Page<AdminUserEntity> mpPage = adminUserRep.page(page, pageSize);
        return PageResult.of(mpPage.getTotal(), mpPage.getRecords().stream().map(this::toUser).toList());
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
     * 棣栨�″惎鍔ㄥ垵濮嬪寲锛氳〃涓虹┖鏃跺垱寤洪厤缃�鎻愪緵鐨勯粯璁ょ�＄悊鍛樿处鍙凤紙SUPER_ADMIN锛?
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
                .id(entity.getId())
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
                .id(user.getId())
                .username(user.getUsername())
                .passwordHash(user.getPasswordHash())
                .enabled(user.isEnabled())
                .role(user.getRole())
                .nickname(user.getNickname())
                .passwordChangedAt(user.getPasswordChangedAt())
                .build();
    }
}

