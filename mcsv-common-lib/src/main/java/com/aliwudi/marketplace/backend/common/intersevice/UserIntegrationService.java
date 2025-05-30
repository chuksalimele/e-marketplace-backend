// package com.aliwudi.marketplace.backend.common.intersevice;
// UserIntegrationService.java
package com.aliwudi.marketplace.backend.common.intersevice;

import com.aliwudi.marketplace.backend.common.dto.UserDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class UserIntegrationService {

    private final WebClient webClient;

    // Assuming your User Service URL is configured in application.properties/yml
    public UserIntegrationService(@Value("${user.service.url}") String userServiceBaseUrl) {
        this.webClient = WebClient.builder().baseUrl(userServiceBaseUrl).build();
    }

    public Mono<UserDto> getUserDtoById(Long userId) {
        return webClient.get()
                .uri("/api/users/{userId}", userId) // Adjust this URI based on your User Service API
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                    clientResponse -> clientResponse.bodyToMono(String.class)
                        .flatMap(errorBody -> Mono.error(new RuntimeException("Error from User Service for user ID " + userId + ": " + errorBody))))
                .bodyToMono(UserDto.class)
                .onErrorResume(e -> {
                    // Log the error but don't rethrow as ResourceNotFoundException from here,
                    // let the calling service decide how to handle a missing user.
                    System.err.println("Failed to fetch user " + userId + " from User Service: " + e.getMessage());
                    return Mono.empty(); // Emit empty to signal not found or error
                });
    }
}