package com.lezai.taskflow.executor;

import com.lezai.taskflow.task.TaskContext;
import com.lezai.taskflow.TaskExecutionException;

import java.util.Optional;

public class TaskExecutor<T, R> {

    public void beforeExecute(TaskContext<T, R> ctx) {
    }

    public void afterExecute(TaskContext<T, R> ctx) {
    }

    public R execute(TaskContext<T, R> ctx) {
        beforeExecute(ctx);
        Optional.ofNullable(ctx.getTaskFlow()).ifPresentOrElse(taskFlow -> taskFlow.run(ctx),
                () -> {
                    throw new TaskExecutionException("task flow is null");
                });
        afterExecute(ctx);
        return ctx.get();
    }
}
