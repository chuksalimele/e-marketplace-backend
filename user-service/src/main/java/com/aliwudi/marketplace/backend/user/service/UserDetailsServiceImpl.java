package com.aliwudi.marketplace.backend.user.service;

import com.aliwudi.marketplace.backend.user.model.User;
import com.aliwudi.marketplace.backend.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService; // NEW: Import ReactiveUserDetailsService
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono; // NEW: Import Mono

@Service
public class UserDetailsServiceImpl implements ReactiveUserDetailsService {

    private final UserRepository userRepository; // Ensure this is a reactive repository (e.g., extends ReactiveCrudRepository or R2dbcRepository)

    @Autowired
    public UserDetailsServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // This method is called by Spring Security during the authentication process.
    // It now returns a Mono<UserDetails> for non-blocking operations.
    @Override
    @Transactional // Keep for reactive transaction management if needed
    public Mono<UserDetails> findByUsername(String username) {
        // Attempt to find the user by username in the database reactively
        return userRepository.findByUsername(username) // Assuming findByUsername returns Mono<User>
                .switchIfEmpty(Mono.error(new UsernameNotFoundException("User Not Found with username: " + username)))
                .map(UserDetailsImpl::build); // Build and return our custom UserDetailsImpl object
    }
}