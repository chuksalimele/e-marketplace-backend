package com.aliwudi.marketplace.backend.common.dto;

import com.aliwudi.marketplace.backend.common.enumeration.ERole;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode; // Keep for now, but will remove 'exclude' for cart

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users",
        uniqueConstraints = {
            @UniqueConstraint(columnNames = "username"),
            @UniqueConstraint(columnNames = "email")
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
// IMPORTANT: Removed 'exclude = {"cart"}' as Cart is no longer part of this entity
@EqualsAndHashCode // If you have other fields you want to exclude, add them here.
public class UserDto {
    private Long id;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private String phoneNumber;
    private String shippingAddress;
    private Set<RoleDto> roles = new HashSet<>();
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}