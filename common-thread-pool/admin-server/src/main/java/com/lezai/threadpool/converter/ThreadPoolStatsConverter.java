package com.lezai.threadpool.converter;

import com.lezai.threadpool.bean.ThreadPoolStats;
import com.lezai.threadpool.dao.entity.ThreadPoolStatsEntity;
import com.lezai.threadpool.pojo.dto.ThreadPoolStatsDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.List;

/**
 * 线程池统计信息转换器
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ThreadPoolStatsConverter {

    /**
     * 将实体转换为 DTO
     */
    ThreadPoolStatsDto convertDto(ThreadPoolStatsEntity entity);

    /**
     * 将实体列表转换为 StatsAppDto 列表
     */
    List<ThreadPoolStatsDto> convertDtos(List<ThreadPoolStatsEntity> entities);

    /**
     * 将 ThreadPoolStats 转换为实体
     */
    @Mapping(target = "appId", expression = "java(appId)")
    ThreadPoolStatsEntity convertEntity(ThreadPoolStats stats, String appId);

    /**
     * 将 DTO 转换为 ThreadPoolStats
     */
    ThreadPoolStats convertStats(ThreadPoolStatsDto dto);

    /**
     * 将 ThreadPoolStats 列表转换为实体列表
     */
    List<ThreadPoolStats> convertStatsBatch(List<ThreadPoolStatsDto> dtos);
}
