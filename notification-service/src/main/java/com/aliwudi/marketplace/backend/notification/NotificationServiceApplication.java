package com.aliwudi.marketplace.backend.notification; // Or the appropriate base package for your notification service

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories; // Enable R2DBC repositories
import org.springframework.context.annotation.ComponentScan; // To ensure all components are scanned
import org.springframework.context.annotation.FilterType;
import org.springframework.transaction.annotation.EnableTransactionManagement; // For @Transactional

/**
 * Main entry point for the Notification Service microservice.
 * This class configures and launches the Spring Boot application.
 */
@SpringBootApplication
@EnableR2dbcRepositories
@EnableTransactionManagement // Enable Spring's annotation-driven transaction management for R2DBC
@EnableDiscoveryClient // Enables this application to act as a Eureka client
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
