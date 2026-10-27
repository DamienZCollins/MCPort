package com.mcport.entity;

import java.util.Arrays;
import java.util.stream.Collectors;

public enum OrderStatus {
    PENDING,
    PAID,
    SHIPPED,
    COMPLETED;

    public static OrderStatus parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("订单状态不能为空");
        }
        try {
            return OrderStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("非法订单状态：" + value + "，可选值：" + allowedValues());
        }
    }

    public static String allowedValues() {
        return Arrays.stream(values()).map(Enum::name).collect(Collectors.joining("/"));
    }
}
