package com.lezai.threadpool.converter;

import com.lezai.threadpool.bean.ThreadPoolStats;
import com.lezai.threadpool.dao.entity.ThreadPoolStatsEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ThreadPoolStatsConverter {

    ThreadPoolStats convertStats(ThreadPoolStatsEntity entity);

    List<ThreadPoolStats> convertStatsBatch(List<ThreadPoolStatsEntity> entities);

    @Mapping(target = "appId", expression = "java(appId)")
    ThreadPoolStatsEntity convertEntity(ThreadPoolStats stats, String appId);
}
