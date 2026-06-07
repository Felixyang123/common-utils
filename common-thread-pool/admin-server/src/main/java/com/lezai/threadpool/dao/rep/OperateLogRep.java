package com.lezai.threadpool.dao.rep;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lezai.threadpool.dao.entity.OperateLogEntity;
import com.lezai.threadpool.dao.mapper.OperateLogMapper;
import org.springframework.stereotype.Repository;

@Repository
public class OperateLogRep extends ServiceImpl<OperateLogMapper, OperateLogEntity> {
}
