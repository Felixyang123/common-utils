package com.lezai.taskflow.task;

import com.lezai.taskflow.TaskExecutionException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskContext<T, R> {
    private T input;

    private R output;

    private Throwable throwable;

    private List<AbstractTask<T, R>> tasks;

    private TaskFlow<T, R> taskFlow;

    void addTask(AbstractTask<T, R> task) {
        this.tasks = Optional.ofNullable(this.tasks).orElseGet(ArrayList::new);
        this.tasks.add(task);
    }

    public R get() {
        if (getThrowable() != null) {
            throw new TaskExecutionException(getThrowable());
        }
        return getOutput();
    }
}
