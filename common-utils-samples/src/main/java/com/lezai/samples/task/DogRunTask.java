package com.lezai.samples.task;

import com.lezai.taskflow.task.AbstractTask;
import com.lezai.taskflow.task.TaskContext;

public class DogRunTask extends AbstractTask<Dog, String> {
    @Override
    public void doRun(TaskContext<Dog, String> ctx) {
        ctx.getInput().run();
    }
}
