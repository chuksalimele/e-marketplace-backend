/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.aliwudi.marketplace.backend.product.dto;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SellerRequest {
    
    private Long id;
    private String name;
    private String email;
    private String phnoneNumber;// personal phone number - this can be different from the Store phone number which is the office line    
}
