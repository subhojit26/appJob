package com.JobHunt.appJob;

import com.JobHunt.appJob.config.AppProperties;
import com.JobHunt.appJob.notify.EmailProperties;
import com.JobHunt.appJob.notify.HealthcheckProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({AppProperties.class, EmailProperties.class, HealthcheckProperties.class})
public class AppJobApplication {

	public static void main(String[] args) {
		SpringApplication.run(AppJobApplication.class, args);
		System.out.println("Application started successfully.");
	}

}

