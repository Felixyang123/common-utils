package com.lezai.threadpool.converter;

import com.lezai.threadpool.bean.ThreadPoolConfig;
import com.lezai.threadpool.dao.entity.ThreadPoolConfigAppEntity;
import com.lezai.threadpool.dao.entity.ThreadPoolConfigEntity;
import com.lezai.threadpool.bean.AddConfigAppResult;
import com.lezai.threadpool.pojo.bean.ThreadPoolAppConfig;
import com.lezai.threadpool.pojo.bean.ThreadPoolConfigApp;
import com.lezai.threadpool.pojo.response.ThreadPoolConfigItemResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ThreadPoolConfigConverter {

    ThreadPoolConfigApp convertApp(ThreadPoolConfigAppEntity entity);

    ThreadPoolConfig convertConfig(ThreadPoolConfigEntity entity);

    List<ThreadPoolConfig> convertConfigs(List<ThreadPoolConfigEntity> entities);

    @Mapping(target = "appId", source = "appId")
    ThreadPoolConfigEntity configConvertEntity(ThreadPoolConfig config, String appId);

    void configUpdateEntity(@MappingTarget ThreadPoolConfigEntity entity, ThreadPoolConfig config);

    ThreadPoolConfigItemResponse convertItemResponse(ThreadPoolConfigEntity entity);

    List<ThreadPoolConfigItemResponse> convertItemResponses(List<ThreadPoolConfigEntity> entities);

    AddConfigAppResult convertToResult(ThreadPoolConfigApp app);

    default ThreadPoolAppConfig dtoConvertAppConfig(ThreadPoolConfigApp app) {
        return ThreadPoolAppConfig.builder()
                .appId(app.getAppId())
                .configVersion(app.getVersion())
                .configs(app.getConfigs())
                .build();
    }

    default ThreadPoolConfigApp buildConfigApp(ThreadPoolConfigAppEntity configApp,
                                                List<ThreadPoolConfigEntity> configs) {
        ThreadPoolConfigApp configAppDto = convertApp(configApp);
        List<ThreadPoolConfig> configList = convertConfigs(configs);
        configAppDto.setConfigs(configList);
        return configAppDto;
    }
}
