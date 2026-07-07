package com.titanfs.metadata;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MetadataServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(MetadataServerApplication.class, args);
	}
}
