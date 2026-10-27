package com.mcport.dto;

public record InventoryInfo(
        String skuId,
        String warehouseId,
        Integer availableQty,
        Integer reservedQty) {
}
