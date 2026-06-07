package com.lezai.threadpool.service;

import com.lezai.threadpool.dao.entity.OperateLogEntity;
import com.lezai.threadpool.dao.rep.OperateLogRep;
import com.lezai.threadpool.enums.BizType;
import com.lezai.threadpool.enums.OperateType;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OperateLogService {

    private final OperateLogRep logRep;

    @Async(value = "historyRecordExecutor")
    public <T> void log(OperateType operateType, String operator, T content, String bizId, BizType bizType) {
        OperateLogEntity operateLog = OperateLogEntity.of(operateType, content, operator, bizId, bizType.name());
        logRep.save(operateLog);
    }
}
