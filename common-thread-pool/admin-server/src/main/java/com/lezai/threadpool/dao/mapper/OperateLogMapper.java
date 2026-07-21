package com.lezai.threadpool.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lezai.threadpool.dao.entity.OperateLogEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

public interface OperateLogMapper extends BaseMapper<OperateLogEntity> {

    /**
     * 游标分页查询：按 (create_time DESC, id DESC) 排序，通过 (create_time, id) 二元组游标翻页。
     * 避免 OFFSET 深度翻页性能退化与字符串拼接 LIMIT。
     *
     * @param lastCreateTime 上一页最后一条的 create_time，首页传 null
     * @param lastId         上一页最后一条的 id，首页传 null
     * @param limit          每页条数
     * @return 当前页记录
     */
    @Select("<script>SELECT * FROM operate_log WHERE deleted = 0 " +
            "<if test='lastCreateTime != null'> AND (create_time &lt; #{lastCreateTime} OR (create_time = #{lastCreateTime} AND id &lt; #{lastId})) </if>" +
            " ORDER BY create_time DESC, id DESC LIMIT #{limit}</script>")
    List<OperateLogEntity> selectByCursor(@Param("lastCreateTime") LocalDateTime lastCreateTime,
                                          @Param("lastId") Long lastId,
                                          @Param("limit") int limit);
}
