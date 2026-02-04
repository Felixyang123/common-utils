package com.lezai.taskflow.task;

import org.springframework.util.CollectionUtils;

import java.util.List;

public record TaskFlow<T, R>(List<Task<T, R>> tasks) implements Task<T, R> {

    public TaskFlow(Task<T, R> task) {
        this(List.of(task));
    }

    @Override
    public void run(TaskContext<T, R> ctx) {
        if (!CollectionUtils.isEmpty(tasks)) {
            tasks.forEach(task -> task.run(ctx));
        }
    }
}
