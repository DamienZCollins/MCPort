package com.mcport.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mcport.dto.InventoryInfo;
import com.mcport.dto.LockResult;
import com.mcport.entity.Inventory;
import com.mcport.entity.InventoryLock;
import com.mcport.mapper.InventoryLockMapper;
import com.mcport.mapper.InventoryMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {

    private final InventoryMapper inventoryMapper;
    private final InventoryLockMapper inventoryLockMapper;

    public InventoryService(InventoryMapper inventoryMapper, InventoryLockMapper inventoryLockMapper) {
        this.inventoryMapper = inventoryMapper;
        this.inventoryLockMapper = inventoryLockMapper;
    }

    public InventoryInfo query(String skuId, String warehouseId) {
        LambdaQueryWrapper<Inventory> query = new LambdaQueryWrapper<Inventory>()
                .eq(Inventory::getSkuId, skuId);
        if (warehouseId != null && !warehouseId.isBlank()) {
            query.eq(Inventory::getWarehouseId, warehouseId);
        }
        Inventory inventory = inventoryMapper.selectOne(query);
        if (inventory == null) {
            return null;
        }
        return new InventoryInfo(inventory.getSkuId(), inventory.getWarehouseId(),
                inventory.getAvailableQty(), inventory.getReservedQty());
    }

    /**
     * 锁定库存。同一 (orderId, skuId) 重复调用返回幂等结果，不重复扣减。
     *
     * @throws IllegalStateException 库存不足或 SKU 不存在
     */
    @Transactional
    public LockResult lock(String skuId, int quantity, String orderId) {
        InventoryLock existing = inventoryLockMapper.selectOne(new LambdaQueryWrapper<InventoryLock>()
                .eq(InventoryLock::getOrderId, orderId)
                .eq(InventoryLock::getSkuId, skuId));
        if (existing != null) {
            return idempotentResult(skuId, existing.getQuantity(), orderId);
        }

        if (inventoryMapper.lock(skuId, quantity) == 0) {
            Inventory inventory = inventoryMapper.selectOne(new LambdaQueryWrapper<Inventory>()
                    .eq(Inventory::getSkuId, skuId));
            if (inventory == null) {
                throw new IllegalStateException("SKU 不存在：" + skuId);
            }
            throw new IllegalStateException(String.format(
                    "库存不足：sku=%s 可用 %d，请求 %d", skuId, inventory.getAvailableQty(), quantity));
        }

        InventoryLock lockRecord = new InventoryLock();
        lockRecord.setOrderId(orderId);
        lockRecord.setSkuId(skuId);
        lockRecord.setQuantity(quantity);
        try {
            inventoryLockMapper.insert(lockRecord);
        } catch (DuplicateKeyException e) {
            // 并发下另一请求已写入同一流水，回滚本次扣减由事务完成，按幂等返回
            throw new IllegalStateException("该订单已锁定过此 SKU：" + orderId + "/" + skuId, e);
        }

        Inventory after = inventoryMapper.selectOne(new LambdaQueryWrapper<Inventory>()
                .eq(Inventory::getSkuId, skuId));
        return new LockResult(true, orderId, skuId, quantity, after.getAvailableQty(), false);
    }

    private LockResult idempotentResult(String skuId, int lockedQty, String orderId) {
        Inventory inventory = inventoryMapper.selectOne(new LambdaQueryWrapper<Inventory>()
                .eq(Inventory::getSkuId, skuId));
        int remaining = inventory == null ? 0 : inventory.getAvailableQty();
        return new LockResult(true, orderId, skuId, lockedQty, remaining, true);
    }
}
