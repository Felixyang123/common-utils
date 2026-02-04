package com.lezai.samples.task;

import com.lezai.taskflow.executor.TaskStorageExecutor;
import com.lezai.taskflow.task.TaskContext;
import com.lezai.taskflow.task.TaskFlow;

public class DogTaskExecutor extends TaskStorageExecutor<Dog, String> {

    public void execute(Dog dog) {
        DogRunTask dogRunTask = new DogRunTask();
        DogEatTask dogEatTask = new DogEatTask();
        DogSleepTask dogSleepTask = new DogSleepTask();
        DogBarkTask dogBarkTask = new DogBarkTask();
        DogShakeTask dogShakeTask = new DogShakeTask();
        DogRollOverTask dogRollOverTask = new DogRollOverTask();
        DogFetchTask dogFetchTask = new DogFetchTask();
        dogRunTask.attach(dogEatTask);
        dogRunTask.attach(dogSleepTask);
        dogEatTask.attach(dogBarkTask);
        dogEatTask.attach(dogShakeTask);
        dogSleepTask.attach(dogBarkTask);
        dogSleepTask.attach(dogShakeTask);
        dogSleepTask.attach(dogRollOverTask);
        dogShakeTask.attach(dogFetchTask);
        execute(TaskContext.<Dog, String>builder().input(dog).taskFlow(new TaskFlow<>(dogRunTask)).build());
    }
}
