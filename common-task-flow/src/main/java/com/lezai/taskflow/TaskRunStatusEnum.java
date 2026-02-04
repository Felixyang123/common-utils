package com.lezai.taskflow;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum TaskRunStatusEnum {
    /**
     * 初始化，任务创建时状态
     */
    INIT,
    /**
     * 运行中，任务开始执行
     */
    RUNNING,
    /**
     * 任务执行成功
     */
    SUCCESS,
    /**
     * 任务执行失败
     */
    FAILED,
    /**
     * 任务被取消，前置任务执行失败后触发
     */
    CANCELED,
    /**
     * 任务完成，所有子任务执行完成
     */
    COMPLETED,
}
