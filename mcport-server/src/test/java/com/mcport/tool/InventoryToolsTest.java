package com.mcport.tool;

import com.mcport.dto.InventoryInfo;
import com.mcport.dto.LockResult;
import com.mcport.entity.Order;
import com.mcport.governance.auth.ApiKeyContext;
import com.mcport.governance.auth.ApiKeyPrincipal;
import com.mcport.governance.exception.ForbiddenException;
import com.mcport.governance.exception.NotFoundException;
import com.mcport.governance.validator.OwnershipValidator;
import com.mcport.service.InventoryService;
import com.mcport.service.OrderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class InventoryToolsTest {

    private final InventoryService inventoryService = mock(InventoryService.class);
    private final OrderService orderService = mock(OrderService.class);
    private final OwnershipValidator ownershipValidator = mock(OwnershipValidator.class);
    private final InventoryTools tools =
            new InventoryTools(inventoryService, orderService, ownershipValidator);

    @AfterEach
    void tearDown() {
        ApiKeyContext.clear();
    }

    @Test
    void checkInventoryReturnsInfo() {
        when(inventoryService.query("SKU00001", null))
                .thenReturn(new InventoryInfo("SKU00001", "WH-BJ", 100, 5));

        InventoryInfo info = tools.checkInventory("SKU00001", null);

        assertThat(info.availableQty()).isEqualTo(100);
    }

    @Test
    void checkInventoryThrowsWhenMissing() {
        when(inventoryService.query("SKU99999", null)).thenReturn(null);

        assertThatThrownBy(() -> tools.checkInventory("SKU99999", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("库存记录不存在");
    }

    @Test
    void lockInventoryRejectsNonPositiveQuantity() {
        assertThatThrownBy(() -> tools.lockInventory("SKU00001", 0, "ORD_user0_001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("quantity");
        verifyNoInteractions(inventoryService, orderService, ownershipValidator);
    }

    @Test
    void lockInventoryRejectsBlankOrderId() {
        assertThatThrownBy(() -> tools.lockInventory("SKU00001", 3, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("orderId");
        verifyNoInteractions(inventoryService, orderService, ownershipValidator);
    }

    @Test
    void lockInventoryRejectsMissingOrder() {
        when(orderService.findByOrderId("ORD_missing")).thenReturn(null);
        when(ownershipValidator.checkOrderOwnership(null))
                .thenThrow(new NotFoundException("订单不存在"));

        assertThatThrownBy(() -> tools.lockInventory("SKU00001", 3, "ORD_missing"))
                .isInstanceOf(NotFoundException.class);
        verifyNoInteractions(inventoryService);
    }

    @Test
    void lockInventoryRejectsOtherUsersOrder() {
        Order order = order("ORD_user1_001", "user1");
        when(orderService.findByOrderId("ORD_user1_001")).thenReturn(order);
        when(ownershipValidator.checkOrderOwnership(order))
                .thenThrow(new ForbiddenException("无权访问其他用户的订单"));

        assertThatThrownBy(() -> tools.lockInventory("SKU00001", 3, "ORD_user1_001"))
                .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(inventoryService);
    }

    @Test
    void lockInventoryReturnsIdempotentResult() {
        ApiKeyContext.set(new ApiKeyPrincipal("test-key-writer", "user0",
                List.of("READ", "WRITE"), 60));
        Order order = order("ORD_user0_001", "user0");
        when(orderService.findByOrderId("ORD_user0_001")).thenReturn(order);
        when(ownershipValidator.checkOrderOwnership(order)).thenReturn(order);
        when(inventoryService.lock("SKU00001", 3, "ORD_user0_001"))
                .thenReturn(new LockResult(true, "ORD_user0_001", "SKU00001", 3, 97, true));

        LockResult result = tools.lockInventory("SKU00001", 3, "ORD_user0_001");

        assertThat(result.idempotent()).isTrue();
        assertThat(result.remainingQty()).isEqualTo(97);
        verify(inventoryService).lock("SKU00001", 3, "ORD_user0_001");
    }

    private Order order(String orderId, String userId) {
        Order order = new Order();
        order.setOrderId(orderId);
        order.setUserId(userId);
        order.setStatus("PAID");
        return order;
    }
}
