package com.mcport.dto;

import com.mcport.entity.OrderItem;

import java.math.BigDecimal;

public record OrderItemView(String skuId, Integer quantity, BigDecimal price) {

    public static OrderItemView of(OrderItem item) {
        return new OrderItemView(item.getSkuId(), item.getQuantity(), item.getPrice());
    }
}
