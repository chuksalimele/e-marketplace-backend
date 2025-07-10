/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.aliwudi.marketplace.backend.common.constants;

/**
 *
 * @author user
 */
public class EventRoutingKey {
    // --- Binding Definitions ---
    // Routing keys should match what the producer service publishes
    public static final String EMAIL_VERIFICATION_ROUTING_KEY = "email.verification.requested";
    public static final String USER_REGISTERED_ROUTING_KEY = "user.registered";
    public static final String PASSWORD_RESET_ROUTING_KEY = "password.reset.requested";
    
}
