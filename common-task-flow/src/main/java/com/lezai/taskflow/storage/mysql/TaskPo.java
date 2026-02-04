package com.lezai.taskflow.storage.mysql;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lezai.taskflow.TaskRunStatusEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TableName(value = "task", autoResultMap = true)
public class TaskPo {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;

    private String name;

    /**
     * @see TaskRunStatusEnum
     */
    private String runStatus;

    private Date createTime;

    private Date updateTime;
}
