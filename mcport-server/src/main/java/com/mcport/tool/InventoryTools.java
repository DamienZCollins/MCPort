package com.mcport.tool;

import com.mcport.dto.InventoryInfo;
import com.mcport.dto.LockResult;
import com.mcport.governance.aspect.GovernedTool;
import com.mcport.governance.auth.ApiKeyPrincipal;
import com.mcport.governance.validator.OwnershipValidator;
import com.mcport.service.InventoryService;
import com.mcport.service.OrderService;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class InventoryTools {

    private final InventoryService inventoryService;
    private final OrderService orderService;
    private final OwnershipValidator ownershipValidator;

    public InventoryTools(InventoryService inventoryService, OrderService orderService,
                          OwnershipValidator ownershipValidator) {
        this.inventoryService = inventoryService;
        this.orderService = orderService;
        this.ownershipValidator = ownershipValidator;
    }

    @GovernedTool(permission = ApiKeyPrincipal.READ)
    @McpTool(name = "check_inventory", description = "查询 SKU 可用库存与预留库存",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false))
    public InventoryInfo checkInventory(
            @McpToolParam(description = "SKU ID", required = true) String skuId,
            @McpToolParam(description = "仓库 ID，留空返回该 SKU 默认仓库", required = false) String warehouseId) {
        if (skuId == null || skuId.isBlank()) {
            throw new IllegalArgumentException("skuId 不能为空");
        }
        InventoryInfo info = inventoryService.query(skuId, warehouseId);
        if (info == null) {
            throw new IllegalArgumentException("库存记录不存在：sku=" + skuId
                    + (warehouseId == null ? "" : " warehouse=" + warehouseId));
        }
        return info;
    }

    @GovernedTool(permission = ApiKeyPrincipal.WRITE)
    @McpTool(name = "lock_inventory", description = "锁定库存并写入锁定流水（需要 WRITE 权限），同一订单重复锁定幂等",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false))
    public LockResult lockInventory(
            @McpToolParam(description = "SKU ID", required = true) String skuId,
            @McpToolParam(description = "锁定数量，必须大于 0", required = true) Integer quantity,
            @McpToolParam(description = "订单 ID，用于幂等控制", required = true) String orderId) {
        if (skuId == null || skuId.isBlank()) {
            throw new IllegalArgumentException("skuId 不能为空");
        }
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("orderId 不能为空");
        }
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("quantity 必须大于 0");
        }
        // orderId 决定幂等键，必须先确认订单存在且属于调用方，否则可借他人订单号占位
        ownershipValidator.checkOrderOwnership(orderService.findByOrderId(orderId));
        return inventoryService.lock(skuId, quantity, orderId);
    }
}
