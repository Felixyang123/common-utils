package com.lezai.taskflow.executor;

import com.lezai.taskflow.task.TaskContext;
import com.lezai.taskflow.storage.TaskStorage;
import lombok.Setter;

public class TaskStorageExecutor<T, R> extends TaskExecutor<T, R> {
    @Setter
    private TaskStorage taskStorage;

    @Override
    public void afterExecute(TaskContext<T, R> ctx) {
        taskStorage.saveBatch(ctx.getTasks());
    }
}
