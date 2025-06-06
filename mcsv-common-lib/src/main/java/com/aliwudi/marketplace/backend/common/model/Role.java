// Role.java
package com.aliwudi.marketplace.backend.common.model;

import com.aliwudi.marketplace.backend.common.enumeration.ERole;
import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class,
        property = "id")
@Table("roles")
public class Role {
    @Id    
    private Long id;
    
    @Column("name") // Maps to the 'name' column
    private ERole name; // Now of type ERole

    public Role(ERole name) {
        this.name = name;
    }
}