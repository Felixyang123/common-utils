package com.lezai.samples;

import com.lezai.anti.duplicate.annotation.EnableAntiDuplicate;
import com.lezai.idempotent.annotation.EnableIdempotent;
import com.lezai.lock.annotation.EnableLock;
import com.lezai.ratelimit.annotation.EnableRateLimiter;
import com.lezai.samples.cache.annotation.EnableCache;
import com.lezai.taskflow.config.TaskFlowAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@EnableCache
@EnableLock
@EnableAntiDuplicate
@Import(TaskFlowAutoConfiguration.class)
@EnableRateLimiter
@EnableIdempotent
public class CommonUtilsApplication {

	public static void main(String[] args) {
		SpringApplication.run(CommonUtilsApplication.class, args);
	}

}
