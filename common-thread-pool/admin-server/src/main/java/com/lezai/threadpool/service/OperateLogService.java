package com.lezai.threadpool.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lezai.threadpool.bean.PageResult;
import com.lezai.threadpool.dao.entity.OperateLogEntity;
import com.lezai.threadpool.dao.mapper.OperateLogMapper;
import com.lezai.threadpool.enums.BizType;
import com.lezai.threadpool.enums.OperateType;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OperateLogService extends ServiceImpl<OperateLogMapper, OperateLogEntity> {

    @Async(value = "historyRecordExecutor")
    public <T> void log(OperateType operateType, String operator, T content, String bizId, BizType bizType) {
        OperateLogEntity operateLog = OperateLogEntity.of(operateType, content, operator, bizId, bizType.name());
        save(operateLog);
    }

    /**
     * 分页查询操作日志
     *
     * @param page        页码（从1开始）
     * @param pageSize    每页条数
     * @param bizType     业务类型（可选）
     * @param operateType 操作类型（可选）
     * @param operator    操作人（可选，模糊匹配）
     * @param bizId       业务ID（可选，精确匹配）
     * @return 分页结果
     */
    public PageResult<OperateLogEntity> queryLogs(int page, int pageSize,
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
        return PageResult.of(pageResult.getTotal(), pageResult.getRecords());
    }

    /**
     * 获取最近的操作日志（用于仪表盘）
     */
    public List<OperateLogEntity> getRecentLogs(int limit) {
        return list(Wrappers.<OperateLogEntity>lambdaQuery()
                .orderByDesc(OperateLogEntity::getCreateTime)
                .last("LIMIT " + Math.max(1, limit)));
    }
}
