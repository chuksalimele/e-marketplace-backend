// Role.java
package com.aliwudi.marketplace.backend.common.model;

import com.aliwudi.marketplace.backend.common.enumeration.ERole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table("roles")
public class Role {
    @Id    
    private Long id;
    private ERole name; // Now of type ERole

    public Role(ERole name) {
        this.name = name;
    }
}