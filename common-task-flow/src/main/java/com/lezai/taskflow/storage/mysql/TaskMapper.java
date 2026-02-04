package com.lezai.taskflow.storage.mysql;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;

public interface TaskMapper extends BaseMapper<TaskPo> {

    @Select("select max(task_id) from task")
    Long getMaxTaskId();
}
