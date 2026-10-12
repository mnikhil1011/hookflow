package com.hookflow;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class HookflowApplication {

	public static void main(String[] args) {
		// Run in UTC regardless of the machine's timezone, so timestamps are consistent
		// everywhere (and Postgres doesn't reject legacy zone names like "Asia/Calcutta").
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
		SpringApplication.run(HookflowApplication.class, args);
	}

}
