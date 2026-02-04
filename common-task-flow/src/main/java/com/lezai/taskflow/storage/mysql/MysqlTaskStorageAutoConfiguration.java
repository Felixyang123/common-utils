package com.lezai.taskflow.storage.mysql;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.lezai.taskflow.storage.mysql")
@ConditionalOnProperty(name = "taskflow.storage", havingValue = "mysql")
public class MysqlTaskStorageAutoConfiguration {

    @Bean
    public TaskRepository taskRepository() {
        return new TaskRepository();
    }

    @Bean
    public TaskRelationRepository taskRelationRepository() {
        return new TaskRelationRepository();
    }

    @Bean
    public MysqlTaskStorage mysqlTaskStorage(TaskRepository taskRepository,
                                             TaskRelationRepository taskRelationRepository) {
        return new MysqlTaskStorage(taskRepository, taskRelationRepository);
    }
}
