/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.aliwudi.marketplace.backend.api.gateway.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;


/**
 * REST Controller for fallback endpoints.
 * These endpoints are hit when a circuit breaker trips,
 * providing a graceful degradation response to the client.
 */
@RestController
class FallbackController {

    @RequestMapping("/fallback")
    public Mono<String> generalFallback() {
        return Mono.just("Service is currently unavailable. Please try again later.");
    }

    @RequestMapping("/fallback/products")
    public Mono<String> productFallback() {
        return Mono.just("Product catalog service is currently unavailable. Please check back soon.");
    }

    @RequestMapping("/fallback/orders")
    public Mono<String> orderFallback() {
        return Mono.just("Order processing service is currently unavailable. Please try again later.");
    }

    @RequestMapping("/fallback/users")
    public Mono<String> userFallback() {
        return Mono.just("User service is currently unavailable. Please try again later.");
    }   

    
    @RequestMapping("/fallback/logistics")
    public Mono<String> logisticsFallback() {
        return Mono.just("Logistics service is currently unavailable. Please try again later.");
    }  
    @RequestMapping("/fallback/media")
    public Mono<String> mediaFallback() {
        return Mono.just("Media service is currently unavailable. Please try again later.");
    }       
}