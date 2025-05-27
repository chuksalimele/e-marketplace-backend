// src/main/java/com/marketplace/emarketplacebackend/model/OrderStatus.java
package com.aliwudi.marketplace.backend.orderprocessing.model;

public enum OrderStatus {
    PENDING,
    PROCESSING,
    SHIPPED,
    DELIVERED,
    CANCELLED,
    RETURNED
}