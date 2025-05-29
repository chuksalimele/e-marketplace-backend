// Role.java
package com.aliwudi.marketplace.backend.user.model;

import com.aliwudi.marketplace.backend.common.enumeration.ERole;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "roles")
@Data
@NoArgsConstructor
// Remove @AllArgsConstructor if you want to explicitly define constructors
// or ensure it generates the one you want.
// If you keep @AllArgsConstructor, it will be: Role(Long id, ERole name)
@AllArgsConstructor // Keep it for now, but be aware of the generated constructor
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Change the type of 'name' from String to ERole
    @Enumerated(EnumType.STRING) // This tells JPA to store the enum name (e.g., "ROLE_ADMIN") as a String in the database
    @Column(nullable = false, unique = true)
    private ERole name; // Now of type ERole

    // Custom constructor to easily create a Role with its name (ERole enum)
    public Role(ERole name) {
        this.name = name;
    }
}