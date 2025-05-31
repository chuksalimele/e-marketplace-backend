package com.aliwudi.marketplace.backend.user.model;

import com.aliwudi.marketplace.backend.common.role.ERole;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("roles")
@Data
@NoArgsConstructor
@AllArgsConstructor // Keep it for now, but be aware of the generated constructor
public class Role {

    @Id
    private Long id;
    private ERole name; // Now of type ERole

    // Custom constructor to easily create a Role with its name (ERole enum)
    public Role(ERole name) {
        this.name = name;
    }
}