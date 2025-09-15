package com.automate.CodeReview;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CodeReviewApplication {
	public static void main(String[] args) {
		SpringApplication.run(CodeReviewApplication.class, args);
	}

}
