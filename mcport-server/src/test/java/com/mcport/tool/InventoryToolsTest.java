package com.mcport.tool;

import com.mcport.dto.InventoryInfo;
import com.mcport.dto.LockResult;
import com.mcport.service.InventoryService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class InventoryToolsTest {

    private final InventoryService inventoryService = mock(InventoryService.class);
    private final InventoryTools tools = new InventoryTools(inventoryService);

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
        verifyNoInteractions(inventoryService);
    }

    @Test
    void lockInventoryRejectsBlankOrderId() {
        assertThatThrownBy(() -> tools.lockInventory("SKU00001", 3, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("orderId");
        verifyNoInteractions(inventoryService);
    }

    @Test
    void lockInventoryReturnsIdempotentResult() {
        when(inventoryService.lock("SKU00001", 3, "ORD_user0_001"))
                .thenReturn(new LockResult(true, "ORD_user0_001", "SKU00001", 3, 97, true));

        LockResult result = tools.lockInventory("SKU00001", 3, "ORD_user0_001");

        assertThat(result.idempotent()).isTrue();
        assertThat(result.remainingQty()).isEqualTo(97);
    }
}
