package com.lezai.threadpool.storage.remote;

import com.lezai.threadpool.bean.AdminUser;
import com.lezai.threadpool.dao.entity.AdminUserEntity;
import com.lezai.threadpool.dao.rep.AdminUserRep;
import com.lezai.threadpool.utils.PasswordUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisMysqlAdminUserStorageTest {

    @Mock
    private AdminUserRep adminUserRep;

    @Captor
    private ArgumentCaptor<AdminUserEntity> entityCaptor;

    private RedisMysqlAdminUserStorage storage;

    @BeforeEach
    void setUp() {
        storage = new RedisMysqlAdminUserStorage(adminUserRep);
    }

    @Test
    @DisplayName("getByUsername maps entity to domain bean when found")
    void getByUsername_found_mapsToDomainBean() {
        AdminUserEntity entity = AdminUserEntity.builder()
                .username("admin")
                .passwordHash("hashed")
                .enabled(true)
                .build();
        when(adminUserRep.findByUsername("admin")).thenReturn(Optional.of(entity));

        Optional<AdminUser> result = storage.getByUsername("admin");

        assertThat(result).isPresent();
        assertThat(result.get().getUsername()).isEqualTo("admin");
        assertThat(result.get().getPasswordHash()).isEqualTo("hashed");
        assertThat(result.get().isEnabled()).isTrue();
    }

    @Test
    @DisplayName("getByUsername returns empty when not found")
    void getByUsername_notFound_returnsEmpty() {
        when(adminUserRep.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThat(storage.getByUsername("ghost")).isEmpty();
    }

    @Test
    @DisplayName("ensureDefaultUser creates default account when table is empty")
    void ensureDefaultUser_emptyTable_createsAccount() {
        when(adminUserRep.existsAny()).thenReturn(false);

        storage.ensureDefaultUser("admin", "changeme");

        verify(adminUserRep).save(entityCaptor.capture());
        AdminUserEntity saved = entityCaptor.getValue();
        assertThat(saved.getUsername()).isEqualTo("admin");
        assertThat(PasswordUtils.matches("changeme", saved.getPasswordHash())).isTrue();
        assertThat(saved.getEnabled()).isTrue();
    }

    @Test
    @DisplayName("ensureDefaultUser is a no-op when any account already exists")
    void ensureDefaultUser_existingAccounts_noOp() {
        when(adminUserRep.existsAny()).thenReturn(true);

        storage.ensureDefaultUser("admin", "changeme");

        verify(adminUserRep, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
