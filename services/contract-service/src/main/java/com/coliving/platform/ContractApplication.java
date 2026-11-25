package com.coliving.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 계약 서비스  시작클래스
 * @author 한성현
 *
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class ContractApplication {

	public static void main(String[] args) {
		SpringApplication.run(ContractApplication.class, args);
	}

}
