package com.loopers.tddstudy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TddstudyApplication {

	public static void main(String[] args) {
		SpringApplication.run(TddstudyApplication.class, args);
	}

}
