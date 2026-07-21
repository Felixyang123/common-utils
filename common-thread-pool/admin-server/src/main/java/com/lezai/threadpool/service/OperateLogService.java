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

import java.time.LocalDateTime;
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
                                                     String operator, String bizId,
                                                     LocalDateTime startTime, LocalDateTime endTime) {
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
        // 强制时间范围：默认最近 7 天，避免全表扫描
        LocalDateTime end = endTime != null ? endTime : LocalDateTime.now();
        LocalDateTime start = startTime != null ? startTime : end.minusDays(7);
        wrapper.between(OperateLogEntity::getCreateTime, start, end);
        wrapper.orderByDesc(OperateLogEntity::getCreateTime);

        Page<OperateLogEntity> pageResult = page(new Page<>(page, pageSize), wrapper);
        List<OperateLogResponse> responses = operateLogConverter.convert(pageResult.getRecords());
        return PageResult.of(pageResult.getTotal(), responses);
    }

    public List<OperateLogResponse> getRecentLogs(int limit) {
        return getRecentLogs(limit, null, null);
    }

    /**
     * 游标分页查询最近日志，消除字符串拼接 LIMIT/OFFSET。
     *
     * @param limit          每页条数（上限 100）
     * @param lastCreateTime 上一页最后一条的 create_time，首页传 null
     * @param lastId         上一页最后一条的 id，首页传 null
     * @return 当前页日志列表
     */
    public List<OperateLogResponse> getRecentLogs(int limit, LocalDateTime lastCreateTime, Long lastId) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        List<OperateLogEntity> entities = getBaseMapper().selectByCursor(lastCreateTime, lastId, safeLimit);
        return operateLogConverter.convert(entities);
    }
}