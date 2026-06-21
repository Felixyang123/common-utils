package com.lezai.threadpool.service;

import com.lezai.threadpool.converter.ApiKeyConverter;
import com.lezai.threadpool.dao.entity.ApiKeyEntity;
import com.lezai.threadpool.dao.mapper.ApiKeyMapper;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ApiKeyPersistenceServiceTest {

    @Mock
    private ApiKeyMapper apiKeyMapper;

    @Mock
    private ApiKeyConverter apiKeyConvertor;

    @Mock
    private OperateLogService logService;

    private ApiKeyPersistenceService service;

    @BeforeEach
    void setUp() {
        service = new ApiKeyPersistenceService(apiKeyConvertor, logService);
        ReflectionTestUtils.setField(service, "baseMapper", apiKeyMapper);
    }

    @Test
    @DisplayName("all returns all API keys")
    void all() {
        ApiKeyEntity entity = ApiKeyEntity.builder().appId("app1").build();
        ApiKeyDto dto = new ApiKeyDto();
        dto.setAppId("app1");

        when(apiKeyMapper.selectList(any())).thenReturn(List.of(entity));
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
        when(apiKeyMapper.selectOne(any(), anyBoolean())).thenReturn(null);
        when(apiKeyMapper.insert(any(ApiKeyEntity.class))).thenReturn(1);

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
        when(apiKeyMapper.selectOne(any(), anyBoolean())).thenReturn(oldEntity);
        when(apiKeyMapper.updateById(any(ApiKeyEntity.class))).thenReturn(1);

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

        when(apiKeyMapper.selectOne(any(), anyBoolean())).thenReturn(entity);
        when(apiKeyConvertor.convertDto(entity)).thenReturn(dto);

        Optional<ApiKeyDto> result = service.findByAppId("app1");

        assertThat(result).isPresent();
        assertThat(result.get().getAppId()).isEqualTo("app1");
    }

    @Test
    @DisplayName("findByAppId returns empty when not found")
    void findByAppId_notFound() {
        when(apiKeyMapper.selectOne(any(), anyBoolean())).thenReturn(null);

        Optional<ApiKeyDto> result = service.findByAppId("unknown");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("deleteByAppId removes the key")
    void deleteByAppId() {
        when(apiKeyMapper.delete(any())).thenReturn(1);

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
        when(apiKeyMapper.updateById(any(ApiKeyEntity.class))).thenReturn(1);

        boolean result = service.update(cmd);

        assertThat(result).isTrue();
        verify(logService).log(eq(OperateType.UPDATE), any(), eq(entity), eq("1"), eq(BizType.APIKEY));
    }
}
