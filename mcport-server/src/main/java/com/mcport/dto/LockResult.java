package com.mcport.dto;

public record LockResult(
        boolean success,
        String orderId,
        String skuId,
        int lockedQty,
        int remainingQty,
        boolean idempotent) {
}
