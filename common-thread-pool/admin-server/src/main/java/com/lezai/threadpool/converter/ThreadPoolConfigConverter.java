package com.lezai.threadpool.converter;

import com.lezai.threadpool.bean.ThreadPoolAppConfig;
import com.lezai.threadpool.bean.ThreadPoolConfig;
import com.lezai.threadpool.dao.entity.ThreadPoolConfigAppEntity;
import com.lezai.threadpool.dao.entity.ThreadPoolConfigEntity;
import com.lezai.threadpool.pojo.cmd.ThreadPoolConfigAppUpsertCmd;
import com.lezai.threadpool.pojo.cmd.ThreadPoolConfigUpsertCmd;
import com.lezai.threadpool.pojo.dto.ThreadPoolConfigAppDto;
import com.lezai.threadpool.pojo.dto.ThreadPoolConfigDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ThreadPoolConfigConverter {

    ThreadPoolConfigAppDto convertDto(ThreadPoolConfigAppEntity entity);

    ThreadPoolConfig convertConfig(ThreadPoolConfigEntity entity);

    List<ThreadPoolConfig> convertConfigs(List<ThreadPoolConfigEntity> entities);

    ThreadPoolConfigDto convertConfigDto(ThreadPoolConfigEntity entity);

    ThreadPoolConfig dtoConvertConfig(ThreadPoolConfigDto dto);

    List<ThreadPoolConfig> dtoConvertConfigBatch(Collection<ThreadPoolConfigDto> dtos);

    List<ThreadPoolConfigDto> convertConfigDtos(List<ThreadPoolConfigEntity> entities);

    ThreadPoolConfigDto configConvertDto(ThreadPoolConfig config);

    @Mapping(target = "version", constant = "0L")
    ThreadPoolConfigAppEntity upsertCmdConvertEntity(ThreadPoolConfigAppUpsertCmd cmd);

    ThreadPoolConfigAppEntity cmdUpdateEntity(@MappingTarget ThreadPoolConfigAppEntity entity, ThreadPoolConfigAppUpsertCmd cmd);

    ThreadPoolConfigEntity upsertCmdConvertEntity(ThreadPoolConfigUpsertCmd cmd);

    void cmdUpdateEntity(@MappingTarget ThreadPoolConfigEntity entity, ThreadPoolConfigUpsertCmd cmd);

    ThreadPoolConfigUpsertCmd configConvertUpsertCmd(ThreadPoolConfig config);

    List<ThreadPoolConfigUpsertCmd> configConvertUpsertCmdBatch(List<ThreadPoolConfig> configs);

    default ThreadPoolAppConfig dtoConvertAppConfig(ThreadPoolConfigAppDto dto) {
        return ThreadPoolAppConfig.builder()
                .appId(dto.getAppId())
                .configVersion(dto.getVersion())
                .configs(dtoConvertConfigBatch(dto.getConfigs().values()))
                .build();
    }

    default ThreadPoolConfigAppDto buildConfigAppDto(ThreadPoolConfigAppEntity configApp,
                                                     List<ThreadPoolConfigEntity> configs) {
        ThreadPoolConfigAppDto configAppDto = convertDto(configApp);
        Map<String, ThreadPoolConfigDto> configMap = configs.stream().map(this::convertConfigDto).collect(
                Collectors.toMap(ThreadPoolConfigDto::getPoolName, Function.identity()));
        configAppDto.setConfigs(configMap);
        return configAppDto;
    }
}
