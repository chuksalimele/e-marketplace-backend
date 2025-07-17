/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.aliwudi.marketplace.backend.orderprocessing;

import com.aliwudi.marketplace.backend.common.config.CliOptionsBootstrapper;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 *
 * @author user
 */
@SpringBootApplication
@EnableR2dbcRepositories // Enables R2DBC repositories
@EnableTransactionManagement // Enable Spring's annotation-driven transaction management for R2DBC
@EnableDiscoveryClient // Enables this application to act as a Eureka client
public class OrderProcessingServiceApplication {

    public static void main(String[] args) {
        
        CliOptionsBootstrapper.check(OrderProcessingServiceApplication.class, args);
        
        SpringApplication.run(OrderProcessingServiceApplication.class, args);
    } 
}
