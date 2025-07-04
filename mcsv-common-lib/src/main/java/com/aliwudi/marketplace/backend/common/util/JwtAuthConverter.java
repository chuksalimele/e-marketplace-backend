package com.aliwudi.marketplace.backend.common.util;

import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import reactor.core.publisher.Mono; // NEW IMPORT: for Mono

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Reactive JWT Authentication Converter for Spring WebFlux.
 * This converter extracts authorities and principle name from a JWT and returns
 * a Mono containing a JwtAuthenticationToken, as required by reactive security flows.
 */
public class JwtAuthConverter implements Converter<Jwt, Mono<? extends AbstractAuthenticationToken>> { // CHANGED return type

    private final JwtGrantedAuthoritiesConverter jwtGrantedAuthoritiesConverter =
            new JwtGrantedAuthoritiesConverter();

    private final String principleAttribute;

    private final String resourceId;

    public JwtAuthConverter(String principleAttribute, String resourceId){
        this.principleAttribute = principleAttribute;
        this.resourceId = resourceId;
    }

    @Override
    public Mono<? extends AbstractAuthenticationToken> convert(@NonNull Jwt jwt) { // CHANGED return type
        Collection<GrantedAuthority> authorities = Stream.concat(
                jwtGrantedAuthoritiesConverter.convert(jwt).stream(),
                extractResourceRoles(jwt).stream()
        ).collect(Collectors.toSet());

        // CRUCIAL CHANGE: Wrap the JwtAuthenticationToken in Mono.just()
        return Mono.just(new JwtAuthenticationToken(
                jwt,
                authorities,
                getPrincipleClaimName(jwt)
        ));
    }

    private String getPrincipleClaimName(Jwt jwt) {
        String claimName = JwtClaimNames.SUB;
        if (principleAttribute != null) {
            claimName = principleAttribute;
        }
        return jwt.getClaim(claimName);
    }

    private Collection<? extends GrantedAuthority> extractResourceRoles(Jwt jwt) {
        Map<String, Object> resourceAccess;
        Map<String, Object> resource;
        Collection<String> resourceRoles;
        if (jwt.getClaim("resource_access") == null) {
            return Set.of();
        }
        resourceAccess = jwt.getClaim("resource_access");

        if (resourceAccess.get(resourceId) == null) {
            return Set.of();
        }
        resource = (Map<String, Object>) resourceAccess.get(resourceId);

        resourceRoles = (Collection<String>) resource.get("roles");
        return resourceRoles
                .stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .collect(Collectors.toSet());
    }
}
