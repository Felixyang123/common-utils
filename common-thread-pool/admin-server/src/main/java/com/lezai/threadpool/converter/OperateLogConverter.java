package com.lezai.threadpool.converter;

import com.lezai.threadpool.dao.entity.OperateLogEntity;
import com.lezai.threadpool.pojo.response.OperateLogResponse;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface OperateLogConverter {

    OperateLogResponse convert(OperateLogEntity entity);

    List<OperateLogResponse> convert(List<OperateLogEntity> entities);
}