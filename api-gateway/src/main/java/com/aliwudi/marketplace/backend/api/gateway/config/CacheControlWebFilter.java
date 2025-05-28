package com.aliwudi.marketplace.backend.api.gateway.config;

import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * A custom WebFilter to explicitly add Cache-Control headers to all responses.
 * This is useful for ensuring that sensitive API responses are not cached by browsers or intermediaries.
 * It sets:
 * - Cache-Control: no-cache, no-store, max-age=0, must-revalidate
 * - Pragma: no-cache (for HTTP/1.0 compatibility)
 * - Expires: 0 (for older browsers)
 */
@Component
public class CacheControlWebFilter implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // Continue with the filter chain first, then modify the response
        return chain.filter(exchange).doFinally(signalType -> {
            // Get the response headers
            exchange.getResponse().getHeaders().add("Cache-Control", "no-cache, no-store, max-age=0, must-revalidate");
            exchange.getResponse().getHeaders().add("Pragma", "no-cache"); // HTTP/1.0 compatibility
            exchange.getResponse().getHeaders().add("Expires", "0"); // Older browser compatibility
        });
    }
}
