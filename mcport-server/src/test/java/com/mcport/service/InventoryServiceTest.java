package com.mcport.service;

import com.mcport.dto.LockResult;
import com.mcport.entity.Inventory;
import com.mcport.entity.InventoryLock;
import com.mcport.mapper.InventoryLockMapper;
import com.mcport.mapper.InventoryMapper;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InventoryServiceTest {

    private final InventoryMapper inventoryMapper = mock(InventoryMapper.class);
    private final InventoryLockMapper lockMapper = mock(InventoryLockMapper.class);
    private final InventoryService service = new InventoryService(inventoryMapper, lockMapper);

    @Test
    void locksAndReturnsRemainingQty() {
        when(lockMapper.selectOne(any())).thenReturn(null);
        when(inventoryMapper.lock("SKU00001", 3)).thenReturn(1);
        when(inventoryMapper.selectOne(any())).thenReturn(inventory(97));

        LockResult result = service.lock("SKU00001", 3, "ORD_1");

        assertThat(result.success()).isTrue();
        assertThat(result.idempotent()).isFalse();
        assertThat(result.remainingQty()).isEqualTo(97);
        verify(lockMapper).insert(any(InventoryLock.class));
    }

    @Test
    void repeatLockReturnsFirstResultWithoutDeducting() {
        InventoryLock existing = new InventoryLock();
        existing.setOrderId("ORD_1");
        existing.setSkuId("SKU00001");
        existing.setQuantity(3);
        when(lockMapper.selectOne(any())).thenReturn(existing);
        when(inventoryMapper.selectOne(any())).thenReturn(inventory(97));

        LockResult result = service.lock("SKU00001", 5, "ORD_1");

        assertThat(result.idempotent()).isTrue();
        assertThat(result.lockedQty()).isEqualTo(3);
        verify(inventoryMapper, never()).lock(any(), any(Integer.class));
        verify(lockMapper, never()).insert(any(InventoryLock.class));
    }

    @Test
    void reportsInsufficientStock() {
        when(lockMapper.selectOne(any())).thenReturn(null);
        when(inventoryMapper.lock("SKU00001", 999)).thenReturn(0);
        when(inventoryMapper.selectOne(any())).thenReturn(inventory(10));

        assertThatThrownBy(() -> service.lock("SKU00001", 999, "ORD_1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("库存不足");
    }

    @Test
    void reportsMissingSku() {
        when(lockMapper.selectOne(any())).thenReturn(null);
        when(inventoryMapper.lock("SKU99999", 1)).thenReturn(0);
        when(inventoryMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.lock("SKU99999", 1, "ORD_1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SKU 不存在");
    }

    @Test
    void concurrentDuplicateLockIsReportedAsConflict() {
        when(lockMapper.selectOne(any())).thenReturn(null);
        when(inventoryMapper.lock("SKU00001", 3)).thenReturn(1);
        when(lockMapper.insert(any(InventoryLock.class))).thenThrow(new DuplicateKeyException("dup"));

        assertThatThrownBy(() -> service.lock("SKU00001", 3, "ORD_1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("已锁定过");
    }

    private Inventory inventory(int available) {
        Inventory inventory = new Inventory();
        inventory.setSkuId("SKU00001");
        inventory.setAvailableQty(available);
        inventory.setReservedQty(5);
        return inventory;
    }
}
