package com.lezai.samples.controller;

import com.lezai.samples.task.Dog;
import com.lezai.samples.task.DogTaskExecutor;
import com.lezai.taskflow.storage.mysql.MysqlTaskStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/task")
@RequiredArgsConstructor
public class TaskController {
    private final MysqlTaskStorage taskStorage;

    @PostMapping("/dog")
    public String dogTask() {
        DogTaskExecutor dogTaskExecutor = new DogTaskExecutor();
        dogTaskExecutor.setTaskStorage(taskStorage);
        dogTaskExecutor.execute(new Dog("heipi", "balck"));
        return "success";
    }
}
