/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Enum.java to edit this template
 */
package com.aliwudi.marketplace.backend.common.enumeration;

/**
 * Defines custom header names for basic authentication information,
 * allowing for flexible and descriptive string representations beyond
 * the enum constant names.
 */
public enum BasicAuthHeaders {
    // Each enum constant now takes a string parameter in its constructor
    X_USER_AUTH_ID("X-User-Auth-ID"),// ID by authorization server
    X_USER_ID("X-User-ID"), // ID by registration on this backend database
    X_USER_EMAIL("X-User-Email"),
    X_USER_PHONE("X-User-Phone"),
    X_USER_FIRST_NAME("X-User-First-Name"),
    X_USER_LAST_NAME("X-User-Last-Name"),
    X_USER_ROLES("X-User-Roles");

    // 1. Private field to hold the custom string value
    private final String headerName;

    // 2. Constructor to initialize the private field
    BasicAuthHeaders(String headerName) {
        this.headerName = headerName;
    }

    // 3. Public getter method to retrieve the custom string value
    public String getHeaderName() {
        return headerName;
    }

    @Override
    public String toString() {
        return headerName;
    }
}
