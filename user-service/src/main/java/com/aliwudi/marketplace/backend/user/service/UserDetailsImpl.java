// UserDetailsImpl.java
package com.aliwudi.marketplace.backend.user.service;

import com.aliwudi.marketplace.backend.common.model.User;
import com.fasterxml.jackson.annotation.JsonIgnore; // To ignore password during serialization
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

// This class implements Spring Security's UserDetails interface.
// It acts as an adapter to convert our User entity into a format Spring Security understands.
public class UserDetailsImpl implements UserDetails {
    private static final long serialVersionUID = 1L; // For serialization

    private Long id;
    private String username;
    private String email;

    @JsonIgnore // Ensures the password is not serialized into JSON responses
    private String password;

    // List of authorities (roles) granted to the user
    private Collection<? extends GrantedAuthority> authorities;

    public UserDetailsImpl(Long id, String username, String email, String password,
                           Collection<? extends GrantedAuthority> authorities) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.password = password;
        this.authorities = authorities;
    }

    // Static method to build a UserDetailsImpl object from our User entity
    public static UserDetailsImpl build(User user) {
        // Map user's roles to Spring Security's GrantedAuthority
        List<GrantedAuthority> authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority(role.getName().name()))
                .collect(Collectors.toList());

        return new UserDetailsImpl(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getPassword(),
                authorities);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    // Account status methods (we'll set them all to true for now)
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    // For comparison purposes (e.g., checking if two UserDetailsImpl objects are the same)
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserDetailsImpl user = (UserDetailsImpl) o;
        return Objects.equals(id, user.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    // Getters for additional user properties (optional, but useful)
    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }
}