package com.lezai.taskflow.storage.mysql;

import com.lezai.taskflow.TaskRelationTypeEnum;
import com.lezai.taskflow.storage.TaskStorage;
import com.lezai.taskflow.task.AbstractTask;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
public class MysqlTaskStorage implements TaskStorage {
    private final TaskRepository taskRepository;
    private final TaskRelationRepository taskRelationRepository;

    @Transactional(rollbackFor = Exception.class)
    @Override
    public <T, R> void save(AbstractTask<T, R> task) {
        taskRepository.save(TaskPo.builder().taskId(task.getTaskId()).runStatus(task.getStatus().name()).build());
        List<TaskRelationPo> taskRelationPos = new ArrayList<>();
        addTaskRelation(task, taskRelationPos);
        taskRelationRepository.saveBatch(taskRelationPos);
    }

    private static <T, R> void addTaskRelation(AbstractTask<T, R> task, List<TaskRelationPo> taskRelationPos) {
        Optional.ofNullable(task.getPreTasks()).ifPresent(preTasks -> preTasks.forEach(
                preTask -> taskRelationPos.add(
                        TaskRelationPo.builder()
                                .taskId(task.getTaskId())
                                .relationType(TaskRelationTypeEnum.PREV.name())
                                .attachTaskId(preTask.getTaskId())
                                .build())));

        Optional.ofNullable(task.getNextTasks()).ifPresent(nextTasks -> nextTasks.forEach(
                nextTask -> taskRelationPos.add(
                        TaskRelationPo.builder()
                                .taskId(task.getTaskId())
                                .relationType(TaskRelationTypeEnum.NEXT.name())
                                .attachTaskId(nextTask.getTaskId())
                                .build())));
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public <T, R> void saveBatch(List<AbstractTask<T, R>> tasks) {
        List<TaskPo> taskPos = tasks.stream().map(task -> TaskPo.builder()
                .name(task.getClass().getSimpleName()).taskId(task.getTaskId()).runStatus(task.getStatus().name())
                .build()).toList();
        taskRepository.saveBatch(taskPos);
        List<TaskRelationPo> taskRelationPos = new ArrayList<>();
        tasks.forEach(task -> addTaskRelation(task, taskRelationPos));
        taskRelationRepository.saveBatch(taskRelationPos);
    }
}
