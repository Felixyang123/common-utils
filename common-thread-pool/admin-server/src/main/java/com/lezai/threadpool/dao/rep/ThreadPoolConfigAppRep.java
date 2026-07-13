package com.lezai.threadpool.dao.rep;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lezai.threadpool.dao.entity.ThreadPoolConfigAppEntity;
import com.lezai.threadpool.dao.mapper.ThreadPoolConfigAppMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class ThreadPoolConfigAppRep extends ServiceImpl<ThreadPoolConfigAppMapper, ThreadPoolConfigAppEntity> {

    public Optional<ThreadPoolConfigAppEntity> findByAppId(String appId) {
        return Optional.ofNullable(this.getOne(Wrappers.<ThreadPoolConfigAppEntity>lambdaQuery()
                .eq(ThreadPoolConfigAppEntity::getAppId, appId)));
    }

    public List<String> allAppIds() {
        return list(Wrappers.<ThreadPoolConfigAppEntity>lambdaQuery()
                .select(ThreadPoolConfigAppEntity::getAppId))
                .stream()
                .map(ThreadPoolConfigAppEntity::getAppId)
                .toList();
    }
}
