/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.aliwudi.marketplace.backend.common.dto;

import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BasicProfile {
    
    private String authId;
    private Long userId;
    private String email;
    private String phoneNumber;
    private String firstName;
    private String lastName;    
    private String primaryIdentifierType; // "EMAIL" or "PHONE_NUMBER" - This is crucial    
    private Set<String> roles;
}
