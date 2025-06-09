/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.aliwudi.marketplace.backend.common.enumeration;

/**
 * Enumeration for different types of notifications.
 * This helps categorize notifications and allows users/systems to filter them.
 */
public enum NotificationType {
    ORDER_UPDATE,       // e.g., Order Placed, Shipped, Delivered, Canceled
    PROMOTION,          // e.g., New discount codes, flash sales
    SYSTEM_ALERT,       // e.g., Downtime, service updates
    MESSAGE,            // e.g., New message from seller/buyer
    REVIEW_REMINDER,    // e.g., Remind user to leave a review
    ACCOUNT_UPDATE,     // e.g., Password changed, profile updated
    WISH_LIST_ITEM_AVAILABLE // e.g., Item in wishlist is now in stock
}

