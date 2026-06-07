package com.lezai.threadpool;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.lezai.threadpool.dao.mapper")
public class ThreadPoolAdminServer {

    public static void main(String[] args) {
        SpringApplication.run(ThreadPoolAdminServer.class, args);
    }
}
