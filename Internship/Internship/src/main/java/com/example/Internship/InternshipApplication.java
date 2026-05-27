package com.example.Internship;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class InternshipApplication {

	public static void main(String[] args) {
		SpringApplication.run(InternshipApplication.class, args);
	}

}
