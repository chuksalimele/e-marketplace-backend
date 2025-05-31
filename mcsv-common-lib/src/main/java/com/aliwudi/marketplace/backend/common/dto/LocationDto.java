/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.aliwudi.marketplace.backend.common.dto;

import com.aliwudi.marketplace.backend.common.enumeration.LocationCategory;

/**
 *
 * @author user
 */
public class LocationDto {
    Long id;
    String country;
    String state;
    String city;
    LocationCategory locationCategory;
}
