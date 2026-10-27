package com.mcport.dto;

import com.mcport.entity.Order;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderView(
        String orderId,
        String userId,
        String status,
        BigDecimal totalAmount,
        String remark,
        LocalDateTime createdAt) {

    public static OrderView of(Order order) {
        return new OrderView(order.getOrderId(), order.getUserId(), order.getStatus(),
                order.getTotalAmount(), order.getRemark(), order.getCreatedAt());
    }
}
