package com.lezai.taskflow.task;

import com.alibaba.fastjson2.JSON;
import com.lezai.taskflow.TaskExecutionException;
import com.lezai.taskflow.TaskRunStatusEnum;
import com.lezai.taskflow.idgen.RedisIdGenerator;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Data
@Slf4j
public abstract class AbstractTask<T, R> implements Task<T, R> {

    private List<AbstractTask<T, R>> preTasks;

    private List<AbstractTask<T, R>> nextTasks;

    private TaskRunStatusEnum status;

    private Long taskId;

    public AbstractTask() {
        init();
    }

    public void init() {
        this.preTasks = new ArrayList<>();
        this.nextTasks = new ArrayList<>();
        this.status = TaskRunStatusEnum.INIT;
        this.taskId = RedisIdGenerator.INSTANCE.getIdNum();
    }

    public void before(TaskContext<T, R> ctx) {
    }

    public void after(TaskContext<T, R> ctx) {
    }

    public void completed(TaskContext<T, R> ctx) {
    }

    public void attach(AbstractTask<T, R> nextTask) {
        Optional.ofNullable(getNextTasks()).ifPresentOrElse(nextTasks -> nextTasks.add(nextTask),
                () -> {
                    throw new TaskExecutionException("task not init");
                });
        Optional.ofNullable(nextTask.getPreTasks()).ifPresentOrElse(preTasks -> preTasks.add(this),
                () -> {
                    throw new TaskExecutionException("task not init");
                });
    }

    public abstract void doRun(TaskContext<T, R> ctx);

    @Override
    public void run(TaskContext<T, R> ctx) {
        List<AbstractTask<T, R>> preTasks = getPreTasks();
        if (!CollectionUtils.isEmpty(preTasks)) {
            boolean skip = preTasks.stream().anyMatch(task -> task.getStatus() !=
                    TaskRunStatusEnum.SUCCESS);
            if (skip) {
                return;
            }
        }
        ctx.addTask(this);
        if (log.isDebugEnabled()) {
            log.debug("task running, taskName-taskId: {}-{}, input: {} ", this.getClass().getName(), getTaskId(),
                    JSON.toJSONString(ctx.getInput()));
        }
        setStatus(TaskRunStatusEnum.RUNNING);
        before(ctx);
        try {
            doRun(ctx);
            setStatus(TaskRunStatusEnum.SUCCESS);
        } catch (Throwable throwable) {
            log.error("task failed, taskName-taskId: {}-{}, input: {}, error: ", this.getClass().getName(),
                    getTaskId(), JSON.toJSONString(ctx.getInput()), throwable);
            setStatus(TaskRunStatusEnum.FAILED);
            ctx.setThrowable(throwable);
            completed(ctx);
            cancel(getNextTasks(), ctx);
            return;
        }

        if (log.isDebugEnabled()) {
            log.debug("task success, taskName-taskId: {}-{}, output: {} ", this.getClass().getName(), getTaskId(),
                    JSON.toJSONString(ctx.getOutput()));
        }
        after(ctx);
        List<AbstractTask<T, R>> nextTasks = getNextTasks();

        if (CollectionUtils.isEmpty(nextTasks)) {
            setStatus(TaskRunStatusEnum.COMPLETED);
        } else {
            // 递归执行后续任务
            for (AbstractTask<T, R> task : nextTasks) {
                task.run(ctx);
            }
        }

        if (TaskRunStatusEnum.COMPLETED == getStatus()) {
            if (log.isDebugEnabled()) {
                log.debug("task completed, taskName-taskId: {}-{}, output: {} ", this.getClass().getName(), getTaskId(),
                        JSON.toJSONString(ctx.getOutput()));
            }
            completed(ctx);
            if (!CollectionUtils.isEmpty(preTasks)) {
                // 更新前置任务已完成
                for (AbstractTask<T, R> preTask : preTasks) {
                    boolean allCompleted = preTask.getNextTasks().stream().allMatch(task ->
                            task.getStatus() == TaskRunStatusEnum.COMPLETED);
                    if (allCompleted) {
                        preTask.setStatus(TaskRunStatusEnum.COMPLETED);
                    }
                }
            }
        }
    }

    private void cancel(List<AbstractTask<T, R>> tasks, TaskContext<T, R> ctx) {
        if (CollectionUtils.isEmpty(tasks)) {
            return;
        }

        for (AbstractTask<T, R> task : tasks) {
            if (TaskRunStatusEnum.CANCELED != task.getStatus()) {
                ctx.addTask(task);
                task.setStatus(TaskRunStatusEnum.CANCELED);
                cancel(task.getNextTasks(), ctx);
            }
        }
    }
}
