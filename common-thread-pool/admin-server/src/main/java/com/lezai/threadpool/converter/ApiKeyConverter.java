package com.lezai.threadpool.converter;

import com.lezai.threadpool.dao.entity.ApiKeyEntity;
import com.lezai.threadpool.pojo.bean.ApiKey;
import com.lezai.threadpool.pojo.request.CreateAppRequest;
import com.lezai.threadpool.pojo.response.CreateApiKeyResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ApiKeyConverter {

    ApiKey convertApiKey(ApiKeyEntity entity);

    List<ApiKey> convertApiKeys(List<ApiKeyEntity> entities);

    ApiKeyEntity convertEntity(ApiKey key);

    @Mapping(target = "appId", source = "request.appId")
    @Mapping(target = "apiKeyHash", source = "apiKeyHash")
    @Mapping(target = "appName", source = "request.appName")
    @Mapping(target = "enabled", constant = "true")
    @Mapping(target = "createTime", expression = "java(java.time.LocalDateTime.now())")
    @Mapping(target = "expireTime", ignore = true)
    @Mapping(target = "description", source = "request.description")
    ApiKey toEntity(CreateAppRequest request, String apiKey, String apiKeyHash);

    @Mapping(target = "apiKey", source = "plainApiKey")
    @Mapping(target = "appId", source = "apiKey.appId")
    @Mapping(target = "appName", source = "apiKey.appName")
    @Mapping(target = "enabled", source = "apiKey.enabled")
    @Mapping(target = "createTime", source = "apiKey.createTime")
    @Mapping(target = "description", source = "apiKey.description")
    CreateApiKeyResponse toResponse(ApiKey apiKey, String plainApiKey);
}
