package com.lezai.threadpool.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lezai.threadpool.dao.entity.ThreadPoolConfigAppEntity;
import org.apache.ibatis.annotations.Update;

public interface ThreadPoolConfigAppMapper extends BaseMapper<ThreadPoolConfigAppEntity> {

    @Update("update thread_pool_config_app set deleted = 0 where app_id = #{appId} and deleted = 1")
    void restore(String appId);
}
