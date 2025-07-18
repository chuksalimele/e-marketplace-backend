package com.aliwudi.marketplace.backend.common.model;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import lombok.Builder;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Table;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class,
        property = "id")
@Table("users")
public class User {
    @Id
    private Long id;
    private String authId; //id generated by authorization server e.g keycloak 
    private String primaryIdentifierType;
    private String primaryIdentifier; //email or phone number
    private String email;
    private String password; // Stores the *hashed* password   
    private String firstName;
    private String lastName;
    private String phoneNumber;
    private boolean emailVerified;
    private boolean phoneVerified;
    private String shippingAddress;
    private LocalDateTime lastLoginAt;
    private LocalDateTime lastLogoutAt;
    
    private boolean enabled;
    
    @CreatedDate // Automatically populated with creation timestamp
    private LocalDateTime createdAt;

    @LastModifiedDate // Automatically populated with last modification timestamp
    private LocalDateTime updatedAt;

    @Transient // This field is not persisted directly in the 'users' table.
               // It's loaded via a join table or separate query by the repository.
    private Set<Role> roles;
    
    
    /**
     * Helper method to add a role to the user.
     * @param role The role to add.
     */
    public void addRole(Role role) {
        if (this.roles == null) {
            this.roles = new HashSet<>();
        }
        
        this.roles.add(role);
    }

}