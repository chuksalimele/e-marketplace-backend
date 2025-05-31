// RoleDto.java
package com.aliwudi.marketplace.backend.common.dto;

import com.aliwudi.marketplace.backend.common.enumeration.ERole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoleDto {
    private Long id;
    private ERole name; // Now of type ERole

    public RoleDto(ERole name) {
        this.name = name;
    }
}