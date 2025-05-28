// UserDetailsServiceImpl.java
package com.aliwudi.marketplace.backend.user.service;

import com.aliwudi.marketplace.backend.user.model.User;
import com.aliwudi.marketplace.backend.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional; // Good practice for service methods

// This service is responsible for fetching user details from your database.
@Service // Marks this as a Spring Service component
public class UserDetailsServiceImpl implements UserDetailsService {

    @Autowired // Spring will inject the UserRepository here
    UserRepository userRepository;

    // This method is called by Spring Security during the authentication process.
    @Override
    @Transactional // Ensures the entire method runs within a single database transaction
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // Attempt to find the user by username in the database
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User Not Found with username: " + username));

        // Build and return our custom UserDetailsImpl object
        return UserDetailsImpl.build(user);
    }
}