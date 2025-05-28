package com.aliwudi.marketplace.backend.api.gateway.handler;

import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus; // Make sure this import is present
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.DispatcherHandler;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.util.concurrent.TimeoutException;

@Component
@Order(-1)
public class GatewayErrorWebExceptionHandler implements ErrorWebExceptionHandler {

    private final DispatcherHandler dispatcherHandler;
    private final ApplicationContext applicationContext;

    public GatewayErrorWebExceptionHandler(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
        this.dispatcherHandler = this.applicationContext.getBean(DispatcherHandler.class);
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        System.err.println("GatewayErrorWebExceptionHandler caught: " + ex.getMessage());

        boolean shouldFallbackGlobally = false;
        HttpStatus statusCodeToSet = null;

        if (ex instanceof ConnectException || ex instanceof TimeoutException) {
            shouldFallbackGlobally = true;
            statusCodeToSet = HttpStatus.SERVICE_UNAVAILABLE;
        } else if (ex instanceof ResponseStatusException) {
            ResponseStatusException rse = (ResponseStatusException) ex;
            if (rse.getStatusCode().is5xxServerError()) {
                shouldFallbackGlobally = true;
                // Corrected line: Convert HttpStatusCode to HttpStatus
                statusCodeToSet = HttpStatus.valueOf(rse.getStatusCode().value());
            }
        } else if (ex instanceof NotFoundException) {
            shouldFallbackGlobally = true;
            statusCodeToSet = HttpStatus.SERVICE_UNAVAILABLE; // Treat as unavailable service
        }

        if (shouldFallbackGlobally) {
            if (statusCodeToSet != null) {
                exchange.getResponse().setStatusCode(statusCodeToSet);
            } else {
                exchange.getResponse().setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
            }

            ServerWebExchange fallbackExchange = exchange.mutate()
                    .request(exchange.getRequest().mutate().path("/fallback").build())
                    .build();

            return this.dispatcherHandler.handle(fallbackExchange);
        }

        return Mono.error(ex);
    }
}