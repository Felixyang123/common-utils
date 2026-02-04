package com.lezai.taskflow.storage.mysql;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TableName(value = "task_relation", autoResultMap = true)
public class TaskRelationPo {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;

    private Long attachTaskId;

    /**
     * @see com.lezai.taskflow.TaskRelationTypeEnum
     */
    private String relationType;

    private Date createTime;
}
