package com.lezai.taskflow.storage;

import com.lezai.taskflow.task.AbstractTask;

import java.util.List;

public interface TaskStorage {

    <T, R> void save(AbstractTask<T, R> task);

    <T, R> void saveBatch(List<AbstractTask<T, R>> tasks);
}
