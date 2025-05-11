package com.faltio.init;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class KubeMlApplication {
	public static void main(String[] args) {
		SpringApplication app = new SpringApplication(KubeMlApplication.class);
		app.setWebApplicationType(WebApplicationType.NONE);
		app.run(args);
	}
}
