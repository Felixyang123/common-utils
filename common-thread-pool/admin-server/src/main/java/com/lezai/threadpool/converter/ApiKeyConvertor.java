package com.lezai.threadpool.converter;

import com.lezai.threadpool.bean.ApiKey;
import com.lezai.threadpool.dao.entity.ApiKeyEntity;
import com.lezai.threadpool.pojo.cmd.ApiKeyUpsertCmd;
import com.lezai.threadpool.pojo.dto.ApiKeyDto;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ApiKeyConvertor {

    /**
     * 转换为 DTO
     * @param entity
     * @return
     */
    ApiKeyDto convertDto(ApiKeyEntity entity);

    /**
     * 批量转换为 DTO
     * @param entities
     * @return
     */
    List<ApiKeyDto> convertDtos(List<ApiKeyEntity> entities);

    /**
     * 转换为 API Key
     * @param dto
     * @return
     */
    ApiKey convertApiKey(ApiKeyDto dto);

    /**
     * 批量转换为 API Key
     * @param dtos
     * @return
     */
    List<ApiKey> convertApiKeys(List<ApiKeyDto> dtos);

    /**
     * 转换为 Entity
     * @param cmd
     * @return
     */
    ApiKeyEntity convertEntity(ApiKeyUpsertCmd cmd);

    /**
     * 转换为 upsert 命令
     * @param key
     * @return
     */
    ApiKeyUpsertCmd convertUpsertCmd(ApiKey key);
}
