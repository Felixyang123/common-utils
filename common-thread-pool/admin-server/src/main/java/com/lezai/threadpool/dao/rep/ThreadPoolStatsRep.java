package com.lezai.threadpool.dao.rep;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lezai.threadpool.dao.entity.ThreadPoolStatsEntity;
import com.lezai.threadpool.dao.mapper.StatsMapper;
import org.springframework.stereotype.Repository;

@Repository
public class ThreadPoolStatsRep extends ServiceImpl<StatsMapper, ThreadPoolStatsEntity> {
}
