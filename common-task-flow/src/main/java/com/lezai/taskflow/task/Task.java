package com.lezai.taskflow.task;

public interface Task<T, R> {
    void run(TaskContext<T, R> ctx);
}
