// RoleDto.java
package com.aliwudi.marketplace.backend.common.dto;

import com.aliwudi.marketplace.backend.common.enumeration.ERole;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor // Keep it for now, but be aware of the generated constructor
public class RoleDto {
    private Long id;
    private ERole name; // Now of type ERole

    public RoleDto(ERole name) {
        this.name = name;
    }
}