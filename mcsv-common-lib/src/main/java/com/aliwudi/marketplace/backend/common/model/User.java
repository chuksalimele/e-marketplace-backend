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
import org.springframework.data.annotation.Id;
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
    private String username;
    private String email;
    private String password; // Stores the *hashed* password   
    private String firstName;
    private String lastName;
    private String phoneNumber;
    private String shippingAddress;
    @Transient
    private Set<Role> roles;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;    

}