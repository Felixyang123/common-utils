package com.lezai.threadpool.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lezai.threadpool.converter.OperateLogConverter;
import com.lezai.threadpool.dao.entity.OperateLogEntity;
import com.lezai.threadpool.dao.mapper.OperateLogMapper;
import com.lezai.threadpool.enums.BizType;
import com.lezai.threadpool.enums.OperateType;
import com.lezai.threadpool.pojo.bean.PageResult;
import com.lezai.threadpool.pojo.response.OperateLogResponse;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OperateLogService extends ServiceImpl<OperateLogMapper, OperateLogEntity> {

    private final OperateLogConverter operateLogConverter;

    @Async(value = "historyRecordExecutor")
    public <T> void log(OperateType operateType, String operator, T content, String bizId, BizType bizType) {
        OperateLogEntity operateLog = OperateLogEntity.of(operateType, content, operator, bizId, bizType.name());
        save(operateLog);
    }

    public PageResult<OperateLogResponse> queryLogs(int page, int pageSize,
                                                     String bizType, String operateType,
                                                     String operator, String bizId) {
        LambdaQueryWrapper<OperateLogEntity> wrapper = Wrappers.<OperateLogEntity>lambdaQuery();
        if (StringUtils.isNotBlank(bizType)) {
            wrapper.eq(OperateLogEntity::getBizType, bizType);
        }
        if (StringUtils.isNotBlank(operateType)) {
            wrapper.eq(OperateLogEntity::getOperateType, operateType);
        }
        if (StringUtils.isNotBlank(operator)) {
            wrapper.like(OperateLogEntity::getOperator, operator);
        }
        if (StringUtils.isNotBlank(bizId)) {
            wrapper.eq(OperateLogEntity::getBizId, bizId);
        }
        wrapper.orderByDesc(OperateLogEntity::getCreateTime);

        Page<OperateLogEntity> pageResult = page(new Page<>(page, pageSize), wrapper);
        List<OperateLogResponse> responses = operateLogConverter.convert(pageResult.getRecords());
        return PageResult.of(pageResult.getTotal(), responses);
    }

    public List<OperateLogResponse> getRecentLogs(int limit) {
        return getRecentLogs(limit, 0);
    }

    public List<OperateLogResponse> getRecentLogs(int limit, int offset) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        int safeOffset = Math.max(0, offset);
        List<OperateLogEntity> entities = list(Wrappers.<OperateLogEntity>lambdaQuery()
                .orderByDesc(OperateLogEntity::getCreateTime)
                .last("LIMIT " + safeLimit + " OFFSET " + safeOffset));
        return operateLogConverter.convert(entities);
    }
}