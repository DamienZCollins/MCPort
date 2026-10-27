package com.mcport.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mcport.dto.OrderDetail;
import com.mcport.dto.OrderItemView;
import com.mcport.dto.OrderView;
import com.mcport.dto.UpdateResult;
import com.mcport.entity.Order;
import com.mcport.entity.OrderItem;
import com.mcport.entity.OrderStatus;
import com.mcport.mapper.OrderItemMapper;
import com.mcport.mapper.OrderMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class OrderService {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;

    public OrderService(OrderMapper orderMapper, OrderItemMapper orderItemMapper) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
    }

    public List<OrderView> queryByUser(String userId, String status, int limit) {
        LambdaQueryWrapper<Order> query = new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, userId)
                .orderByDesc(Order::getCreatedAt)
                .last("LIMIT " + limit);
        if (status != null && !status.isBlank()) {
            query.eq(Order::getStatus, OrderStatus.parse(status).name());
        }
        return orderMapper.selectList(query).stream().map(OrderView::of).toList();
    }

    public Order findByOrderId(String orderId) {
        return orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getOrderId, orderId));
    }

    public OrderDetail getDetail(Order order) {
        List<OrderItemView> items = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                        .eq(OrderItem::getOrderId, order.getOrderId()))
                .stream().map(OrderItemView::of).toList();
        return new OrderDetail(OrderView.of(order), items);
    }

    @Transactional
    public UpdateResult updateStatus(Order order, String newStatus, String remark) {
        OrderStatus target = OrderStatus.parse(newStatus);
        String oldStatus = order.getStatus();
        order.setStatus(target.name());
        if (remark != null && !remark.isBlank()) {
            order.setRemark(remark);
        }
        orderMapper.updateById(order);
        return new UpdateResult(true, order.getOrderId(), oldStatus, target.name());
    }
}
