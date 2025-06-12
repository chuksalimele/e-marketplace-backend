package com.aliwudi.marketplace.backend.api.gateway.config;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(1) // Ensure this filter runs early, before response is committed
public class CacheControlWebFilter implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // Get the mutable HttpHeaders from the response
        HttpHeaders headers = exchange.getResponse().getHeaders();

        // Add Cache-Control headers
        // Use set to replace existing headers, or add if you want multiple values
        headers.set(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, max-age=0, must-revalidate");
        headers.set(HttpHeaders.PRAGMA, "no-cache");
        headers.set(HttpHeaders.EXPIRES, "0");

        return chain.filter(exchange);
    }
}