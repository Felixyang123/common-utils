package com.lezai.samples;

import com.lezai.lock.annotation.EnableLock;
import com.lezai.samples.cache.annotation.EnableCache;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableCache
@EnableLock
public class CommonUtilsApplication {

	public static void main(String[] args) {
		SpringApplication.run(CommonUtilsApplication.class, args);
	}

}
