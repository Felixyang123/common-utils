package com.lezai.threadpool.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lezai.threadpool.dao.entity.ThreadPoolConfigEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface ThreadPoolConfigMapper extends BaseMapper<ThreadPoolConfigEntity> {

    @Select("SELECT * FROM thread_pool_config WHERE deleted = 1 ORDER BY update_time DESC")
    List<ThreadPoolConfigEntity> selectDeleted();

    @Select("SELECT * FROM thread_pool_config WHERE deleted = 1 AND app_id = #{appId}")
    List<ThreadPoolConfigEntity> selectDeletedByAppId(@Param("appId") String appId);

    @Select("SELECT * FROM thread_pool_config WHERE deleted = 1 AND app_id = #{appId} AND pool_name = #{poolName} LIMIT 1")
    ThreadPoolConfigEntity selectDeletedByAppIdAndPoolName(@Param("appId") String appId, @Param("poolName") String poolName);

    /**
     * 查询指定 appId + poolNames 的全部配置（含 deleted=0 和 deleted=1），一次性拉取、内存中按 deleted 分流。
     * 显式 SELECT 绕过 MyBatis-Plus @TableLogic 过滤。
     */
    @Select("<script>SELECT * FROM thread_pool_config WHERE app_id = #{appId} AND pool_name IN " +
            "<foreach collection='poolNames' item='n' open='(' separator=',' close=')'>#{n}</foreach></script>")
    List<ThreadPoolConfigEntity> selectAllByAppIdAndPoolNamesIn(@Param("appId") String appId, @Param("poolNames") List<String> poolNames);

    @Update("UPDATE thread_pool_config SET deleted = 0 WHERE deleted = 1 and id = #{id}")
    void restore(@Param("id") Long id);

    @Update("UPDATE thread_pool_config SET deleted = 0 WHERE deleted = 1 and app_id = #{appId}")
    void restoreByAppId(String appId);
}
