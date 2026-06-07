package com.lezai.threadpool.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lezai.threadpool.converter.ApiKeyConvertor;
import com.lezai.threadpool.dao.entity.ApiKeyEntity;
import com.lezai.threadpool.dao.rep.ApiKeyRep;
import com.lezai.threadpool.enums.BizType;
import com.lezai.threadpool.enums.OperateType;
import com.lezai.threadpool.pojo.cmd.ApiKeyUpsertCmd;
import com.lezai.threadpool.pojo.dto.ApiKeyDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiKeyServiceTest {

    @Mock
    private ApiKeyRep apiKeyRep;

    @Mock
    private ApiKeyConvertor apiKeyConvertor;

    @Mock
    private OperateLogService logService;

    private ApiKeyService service;

    @BeforeEach
    void setUp() {
        service = new ApiKeyService(apiKeyRep, apiKeyConvertor, logService);
    }

    @Test
    @DisplayName("all returns all API keys")
    void all() {
        ApiKeyEntity entity = ApiKeyEntity.builder().appId("app1").build();
        ApiKeyDto dto = new ApiKeyDto();
        dto.setAppId("app1");

        when(apiKeyRep.list()).thenReturn(List.of(entity));
        when(apiKeyConvertor.convertDtos(List.of(entity))).thenReturn(List.of(dto));

        List<ApiKeyDto> result = service.all();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAppId()).isEqualTo("app1");
    }

    @Test
    @DisplayName("upsert creates new API key when not exists")
    void upsert_create() {
        ApiKeyUpsertCmd cmd = new ApiKeyUpsertCmd();
        cmd.setAppId("app1");
        ApiKeyEntity entity = ApiKeyEntity.builder().appId("app1").build();

        when(apiKeyConvertor.convertEntity(cmd)).thenReturn(entity);
        when(apiKeyRep.getOne(any(Wrapper.class))).thenReturn(null);
        when(apiKeyRep.saveOrUpdate(entity)).thenReturn(true);

        boolean result = service.upsert(cmd);

        assertThat(result).isTrue();
        verify(logService).log(eq(OperateType.CREATE), any(), eq(entity), any(), eq(BizType.APIKEY));
    }

    @Test
    @DisplayName("upsert updates existing API key")
    void upsert_update() {
        ApiKeyUpsertCmd cmd = new ApiKeyUpsertCmd();
        cmd.setAppId("app1");
        ApiKeyEntity newEntity = ApiKeyEntity.builder().appId("app1").build();
        ApiKeyEntity oldEntity = ApiKeyEntity.builder().id(1L).build();

        when(apiKeyConvertor.convertEntity(cmd)).thenReturn(newEntity);
        when(apiKeyRep.getOne(any(Wrapper.class))).thenReturn(oldEntity);
        when(apiKeyRep.saveOrUpdate(newEntity)).thenReturn(true);

        boolean result = service.upsert(cmd);

        assertThat(result).isTrue();
        assertThat(newEntity.getId()).isEqualTo(1L);
        verify(logService).log(eq(OperateType.UPDATE), any(), eq(newEntity), any(), eq(BizType.APIKEY));
    }

    @Test
    @DisplayName("findByAppId returns API key when found")
    void findByAppId_found() {
        ApiKeyEntity entity = ApiKeyEntity.builder().appId("app1").build();
        ApiKeyDto dto = new ApiKeyDto();
        dto.setAppId("app1");

        when(apiKeyRep.getOne(any(Wrapper.class))).thenReturn(entity);
        when(apiKeyConvertor.convertDto(entity)).thenReturn(dto);

        Optional<ApiKeyDto> result = service.findByAppId("app1");

        assertThat(result).isPresent();
        assertThat(result.get().getAppId()).isEqualTo("app1");
    }

    @Test
    @DisplayName("findByAppId returns empty when not found")
    void findByAppId_notFound() {
        when(apiKeyRep.getOne(any(Wrapper.class))).thenReturn(null);

        Optional<ApiKeyDto> result = service.findByAppId("unknown");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("deleteByAppId removes the key")
    void deleteByAppId() {
        when(apiKeyRep.remove(any(Wrapper.class))).thenReturn(true);

        boolean result = service.deleteByAppId("app1");

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("update updates existing key")
    void update() {
        ApiKeyUpsertCmd cmd = new ApiKeyUpsertCmd();
        cmd.setAppId("app1");
        cmd.setId(1L);
        ApiKeyEntity entity = ApiKeyEntity.builder().id(1L).build();

        when(apiKeyConvertor.convertEntity(cmd)).thenReturn(entity);
        when(apiKeyRep.updateById(entity)).thenReturn(true);

        boolean result = service.update(cmd);

        assertThat(result).isTrue();
        verify(logService).log(eq(OperateType.UPDATE), any(), eq(entity), eq("1"), eq(BizType.APIKEY));
    }
}
