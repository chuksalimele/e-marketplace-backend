/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.aliwudi.marketplace.backend.common.model;

import com.aliwudi.marketplace.backend.common.enumeration.LocationCategory;
import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
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
@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class,
        property = "id")
@Table("locations")
public class Location {
    @Id
    Long id;
    String country;
    //String countryAbbr; // Will be implemented in client side - e.g ng for Nigeria, gh for Ghana
    String state;
    //String stateAbbr; // Will be implemented in client side -
    String city;
    LocationCategory locationCategory;
    // Potentially add: private String postalCode;
    // Potentially add: private Double latitude;
    // Potentially add: private Double longitude;    
}
