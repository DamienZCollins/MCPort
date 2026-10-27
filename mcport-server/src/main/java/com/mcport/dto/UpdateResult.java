package com.mcport.dto;

public record UpdateResult(
        boolean success,
        String orderId,
        String oldStatus,
        String newStatus) {
}
