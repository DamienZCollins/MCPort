package com.mcport.entity;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public enum OrderStatus {
    PENDING,
    PAID,
    SHIPPED,
    COMPLETED;

    /**
     * 允许的流转方向。与知识库中「已完成不可回退，已发货只能到已完成」的规则一致；
     * 退款不在本项目的状态模型内。
     */
    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = Map.of(
            PENDING, EnumSet.of(PAID),
            PAID, EnumSet.of(SHIPPED),
            SHIPPED, EnumSet.of(COMPLETED),
            COMPLETED, EnumSet.noneOf(OrderStatus.class));

    /**
     * 同状态视为幂等（重复提交不报错），其余按 {@link #ALLOWED_TRANSITIONS} 判断。
     */
    public boolean canTransitionTo(OrderStatus target) {
        return this == target || ALLOWED_TRANSITIONS.get(this).contains(target);
    }

    public Set<OrderStatus> allowedTargets() {
        return ALLOWED_TRANSITIONS.get(this);
    }

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
