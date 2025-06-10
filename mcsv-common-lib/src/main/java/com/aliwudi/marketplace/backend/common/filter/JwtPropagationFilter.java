package com.aliwudi.marketplace.backend.common.filter;

import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A WebClient filter that extracts the JWT from the ReactiveSecurityContextHolder
 * and adds it as an Authorization Bearer token to outgoing requests.
 * This is useful for propagating the original user's token to downstream microservices.
 */
@Component // Make this a Spring component so it can be injected
public class JwtPropagationFilter implements ExchangeFilterFunction {

    private static final Logger log = LoggerFactory.getLogger(JwtPropagationFilter.class);

    @Override
    public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
        return ReactiveSecurityContextHolder.getContext() // Get the reactive security context
            .flatMap(securityContext -> {
                if (securityContext.getAuthentication() != null &&
                    securityContext.getAuthentication().isAuthenticated() &&
                    securityContext.getAuthentication().getPrincipal() instanceof Jwt) {

                    Jwt jwt = (Jwt) securityContext.getAuthentication().getPrincipal();
                    String tokenValue = jwt.getTokenValue();

                    log.debug("Propagating JWT for user: {}", jwt.getSubject());

                    // Rebuild the request to add the Authorization header
                    ClientRequest authorizedRequest = ClientRequest.from(request)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenValue)
                        .build();
                    return next.exchange(authorizedRequest);
                } else {
                    log.debug("No authenticated JWT found in context. Proceeding without Authorization header.");
                    return next.exchange(request); // No JWT to add, proceed with original request
                }
            })
            .switchIfEmpty(Mono.defer(() -> {
                log.debug("ReactiveSecurityContext is empty. Proceeding without Authorization header.");
                return next.exchange(request); // Context is empty, proceed with original request
            }));
    }
}