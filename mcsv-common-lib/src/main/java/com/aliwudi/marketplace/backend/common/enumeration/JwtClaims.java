/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Enum.java to edit this template
 */
package com.aliwudi.marketplace.backend.common.enumeration;

/**
 *
 * @author user
 */
public enum JwtClaims {
    // Each enum constant now takes a string parameter in its constructor
    sub("sub"),
    userId("userId"), 
    email("email"),
    phone("phone"),
    firstName("firstName"),
    lastName("lastName"),
    emailVerified("emailVerified"),
    phoneVerified("phoneVerified"),
    primaryIdentifierType("primaryIdentifierType"),
    roles("roles");

    // 1. Private field to hold the custom string value
    private final String claimName;

    // 2. Constructor to initialize the private field
    JwtClaims(String claimName) {
        this.claimName = claimName;
    }

    // 3. Public getter method to retrieve the custom string value
    public String getClaimName() {
        return claimName;
    }

    @Override
    public String toString() {
        return claimName;
    }
}