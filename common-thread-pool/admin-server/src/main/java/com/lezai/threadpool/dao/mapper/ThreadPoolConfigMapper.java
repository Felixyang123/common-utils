package com.lezai.threadpool.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lezai.threadpool.dao.entity.ThreadPoolConfigEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

public interface ThreadPoolConfigMapper extends BaseMapper<ThreadPoolConfigEntity> {

    @Select("SELECT app_id, COUNT(*) AS count FROM thread_pool_config WHERE deleted = 0 GROUP BY app_id")
    Map<String, Long> countAllByAppId();

    @Select("SELECT app_id, COUNT(*) AS count FROM thread_pool_config WHERE deleted = 0 AND app_id IN (#{appIds}) GROUP BY app_id")
    Map<String, Long> countByAppIds(@Param("appIds") List<String> appIds);

}
