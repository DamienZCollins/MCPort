package com.mcport.service;

import com.mcport.dto.OrderView;
import com.mcport.dto.UpdateResult;
import com.mcport.entity.Order;
import com.mcport.mapper.OrderItemMapper;
import com.mcport.mapper.OrderMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OrderServiceTest {

    private final OrderMapper orderMapper = mock(OrderMapper.class);
    private final OrderItemMapper orderItemMapper = mock(OrderItemMapper.class);
    private final OrderService service = new OrderService(orderMapper, orderItemMapper);

    @Test
    void queryByUserNormalizesStatusAndPassesLimit() {
        when(orderMapper.selectByUser("user0", "PAID", 5)).thenReturn(List.of(order("ORD_1", "PAID")));

        List<OrderView> views = service.queryByUser("user0", "paid", 5);

        assertThat(views).extracting(OrderView::orderId).containsExactly("ORD_1");
        verify(orderMapper).selectByUser("user0", "PAID", 5);
    }

    @Test
    void blankStatusIsPassedAsNull() {
        when(orderMapper.selectByUser("user0", null, 10)).thenReturn(List.of());

        service.queryByUser("user0", "  ", 10);

        verify(orderMapper).selectByUser("user0", null, 10);
    }

    @Test
    void updatesStatusWithExpectedStatusGuard() {
        when(orderMapper.updateStatus("ORD_1", "PAID", "SHIPPED", "已出库")).thenReturn(1);

        UpdateResult result = service.updateStatus(order("ORD_1", "PAID"), "shipped", "已出库");

        assertThat(result.oldStatus()).isEqualTo("PAID");
        assertThat(result.newStatus()).isEqualTo("SHIPPED");
        verify(orderMapper).updateStatus("ORD_1", "PAID", "SHIPPED", "已出库");
        verify(orderMapper, never()).updateById(any(Order.class));
    }

    @Test
    void blankRemarkIsPassedAsNullToKeepOriginal() {
        when(orderMapper.updateStatus("ORD_1", "PAID", "SHIPPED", null)).thenReturn(1);

        service.updateStatus(order("ORD_1", "PAID"), "SHIPPED", "  ");

        verify(orderMapper).updateStatus("ORD_1", "PAID", "SHIPPED", null);
    }

    @Test
    void sameStatusIsIdempotentWithoutWrite() {
        UpdateResult result = service.updateStatus(order("ORD_1", "PAID"), "paid", null);

        assertThat(result.newStatus()).isEqualTo("PAID");
        verifyNoInteractions(orderMapper);
    }

    @Test
    void rejectsBackwardTransition() {
        assertThatThrownBy(() -> service.updateStatus(order("ORD_1", "COMPLETED"), "PENDING", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("非法状态流转");
        verifyNoInteractions(orderMapper);
    }

    @Test
    void rejectsSkippedTransition() {
        assertThatThrownBy(() -> service.updateStatus(order("ORD_1", "PENDING"), "COMPLETED", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("非法状态流转");
    }

    @Test
    void reportsConflictWhenRowNotUpdated() {
        when(orderMapper.updateStatus(anyString(), anyString(), anyString(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.updateStatus(order("ORD_1", "PAID"), "SHIPPED", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("已被其他请求修改");
    }

    private Order order(String orderId, String status) {
        Order order = new Order();
        order.setOrderId(orderId);
        order.setUserId("user0");
        order.setStatus(status);
        return order;
    }
}
