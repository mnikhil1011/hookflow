package com.hookflow;

import org.springframework.boot.SpringApplication;

public class TestHookflowApplication {

	public static void main(String[] args) {
		SpringApplication.from(HookflowApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
