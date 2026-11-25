package com.coliving.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 회원 서비스 Spring Boot 진입점.
 *
 * @author 조휘재
 * 작성일: 2026-10-16
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class UserApplication {

	/** 애플리케이션을 시작한다. */
	public static void main(String[] args) {
		SpringApplication.run(UserApplication.class, args);
	}

}
