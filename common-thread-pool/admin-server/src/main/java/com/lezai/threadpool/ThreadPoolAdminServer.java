package com.lezai.threadpool;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@MapperScan("com.lezai.threadpool.dao.mapper")
public class ThreadPoolAdminServer {

    public static void main(String[] args) {
        SpringApplication.run(ThreadPoolAdminServer.class, args);
    }
}
