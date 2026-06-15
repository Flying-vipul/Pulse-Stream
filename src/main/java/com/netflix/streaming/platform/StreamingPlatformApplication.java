package com.netflix.streaming.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableRetry
@EnableAsync// <-- Add this!
public class StreamingPlatformApplication {

	public static void main(String[] args) {
		SpringApplication.run(StreamingPlatformApplication.class, args);
	}

}
