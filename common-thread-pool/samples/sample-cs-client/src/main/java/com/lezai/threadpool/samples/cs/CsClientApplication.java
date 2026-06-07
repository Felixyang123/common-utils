package com.lezai.threadpool.samples.cs;

import com.lezai.threadpool.annotation.EnableThreadPool;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableThreadPool
public class CsClientApplication {

    public static void main(String[] args) {
        SpringApplication.run(CsClientApplication.class, args);
    }
}
