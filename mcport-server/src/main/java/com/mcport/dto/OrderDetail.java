package com.mcport.dto;

import java.util.List;

public record OrderDetail(OrderView order, List<OrderItemView> items) {
}
