package com.lezai.samples.idempotent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class SamplesIdempotentApplication {

    public static void main(String[] args) {
        SpringApplication.run(SamplesIdempotentApplication.class, args);
    }
}
