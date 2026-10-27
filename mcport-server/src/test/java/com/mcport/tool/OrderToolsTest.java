package com.mcport.tool;

import com.mcport.dto.OrderDetail;
import com.mcport.dto.OrderItemView;
import com.mcport.dto.OrderView;
import com.mcport.dto.UpdateResult;
import com.mcport.entity.Order;
import com.mcport.entity.OrderStatus;
import com.mcport.governance.auth.ApiKeyContext;
import com.mcport.governance.auth.ApiKeyPrincipal;
import com.mcport.governance.exception.ForbiddenException;
import com.mcport.governance.exception.NotFoundException;
import com.mcport.governance.validator.OwnershipValidator;
import com.mcport.service.OrderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OrderToolsTest {

    private final OrderService orderService = mock(OrderService.class);
    private final OwnershipValidator ownershipValidator = mock(OwnershipValidator.class);
    private final OrderTools tools = new OrderTools(orderService, ownershipValidator);

    @BeforeEach
    void setUp() {
        ApiKeyContext.set(new ApiKeyPrincipal("test-key-read-only", "user0", List.of("READ"), 60));
    }

    @AfterEach
    void tearDown() {
        ApiKeyContext.clear();
    }

    @Test
    void queryOrdersUsesDefaultLimit() {
        when(orderService.queryByUser("user0", null, 10)).thenReturn(List.of());

        tools.queryOrders("user0", null, null);

        verify(orderService).queryByUser("user0", null, 10);
    }

    @Test
    void queryOrdersNormalizesStatusCase() {
        when(orderService.queryByUser("user0", "PAID", 5)).thenReturn(List.of());

        tools.queryOrders("user0", "paid", 5);

        verify(orderService).queryByUser("user0", "PAID", 5);
    }

    @Test
    void queryOrdersRejectsOtherUser() {
        assertThatThrownBy(() -> tools.queryOrders("user1", null, 5))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("其他用户");
        verifyNoInteractions(orderService);
    }

    @Test
    void adminCanQueryOtherUserOrders() {
        ApiKeyContext.set(new ApiKeyPrincipal("test-key-admin", "admin",
                List.of("READ", "WRITE", "ADMIN"), 100));
        when(orderService.queryByUser("user1", null, 5)).thenReturn(List.of());

        tools.queryOrders("user1", null, 5);

        verify(orderService).queryByUser("user1", null, 5);
    }

    @Test
    void queryOrdersRejectsLimitAboveMax() {
        assertThatThrownBy(() -> tools.queryOrders("user0", null, 101))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1-100");
        verifyNoInteractions(orderService);
    }

    @Test
    void queryOrdersRejectsBlankUserId() {
        assertThatThrownBy(() -> tools.queryOrders(" ", null, 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userId");
        verifyNoInteractions(orderService);
    }

    @Test
    void queryOrdersRejectsIllegalStatus() {
        assertThatThrownBy(() -> tools.queryOrders("user0", "CANCELLED", 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("非法订单状态");
    }

    @Test
    void getOrderDetailThrowsWhenOrderMissing() {
        when(orderService.findByOrderId("ORD_missing_001")).thenReturn(null);

        assertThatThrownBy(() -> tools.getOrderDetail("ORD_missing_001"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("订单不存在");
    }

    @Test
    void getOrderDetailReturnsItems() {
        Order order = order("ORD_user0_001", "PAID");
        when(orderService.findByOrderId("ORD_user0_001")).thenReturn(order);
        when(ownershipValidator.checkOrderOwnership(order)).thenReturn(order);
        when(orderService.getDetail(order)).thenReturn(new OrderDetail(
                OrderView.of(order), List.of(new OrderItemView("SKU00001", 2, new BigDecimal("19.90")))));

        OrderDetail detail = tools.getOrderDetail("ORD_user0_001");

        assertThat(detail.order().orderId()).isEqualTo("ORD_user0_001");
        assertThat(detail.items()).hasSize(1);
    }

    @Test
    void updateOrderStatusDelegatesToService() {
        Order order = order("ORD_user0_002", "PAID");
        when(orderService.findByOrderId("ORD_user0_002")).thenReturn(order);
        when(ownershipValidator.checkOrderOwnership(order)).thenReturn(order);
        when(orderService.updateStatus(any(), anyString(), any())).thenReturn(
                new UpdateResult(true, "ORD_user0_002", "PAID", "SHIPPED"));

        UpdateResult result = tools.updateOrderStatus("ORD_user0_002", "shipped", "已出库");

        assertThat(result.newStatus()).isEqualTo(OrderStatus.SHIPPED.name());
        verify(orderService).updateStatus(order, "shipped", "已出库");
    }

    private Order order(String orderId, String status) {
        Order order = new Order();
        order.setOrderId(orderId);
        order.setUserId("user0");
        order.setStatus(status);
        order.setTotalAmount(new BigDecimal("99.00"));
        return order;
    }
}
