package com.mcport.tool;

import com.mcport.dto.OrderDetail;
import com.mcport.dto.OrderView;
import com.mcport.dto.UpdateResult;
import com.mcport.entity.Order;
import com.mcport.entity.OrderStatus;
import com.mcport.governance.aspect.GovernedTool;
import com.mcport.governance.auth.ApiKeyContext;
import com.mcport.governance.auth.ApiKeyPrincipal;
import com.mcport.governance.exception.ForbiddenException;
import com.mcport.governance.exception.NotFoundException;
import com.mcport.governance.validator.OwnershipValidator;
import com.mcport.service.OrderService;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OrderTools {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 100;

    private final OrderService orderService;
    private final OwnershipValidator ownershipValidator;

    public OrderTools(OrderService orderService, OwnershipValidator ownershipValidator) {
        this.orderService = orderService;
        this.ownershipValidator = ownershipValidator;
    }

    @GovernedTool
    @McpTool(name = "query_orders", description = "查询用户订单列表，支持按状态过滤",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false))
    public List<OrderView> queryOrders(
            @McpToolParam(description = "用户 ID", required = true) String userId,
            @McpToolParam(description = "订单状态：PENDING/PAID/SHIPPED/COMPLETED，留空返回全部", required = false)
            String status,
            @McpToolParam(description = "返回记录数，默认 10，最大 100", required = false) Integer limit) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId 不能为空");
        }
        int effectiveLimit = limit == null ? DEFAULT_LIMIT : limit;
        if (effectiveLimit < 1 || effectiveLimit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit 取值范围 1-" + MAX_LIMIT);
        }
        ApiKeyPrincipal principal = ApiKeyContext.require();
        if (!principal.isAdmin() && !principal.userId().equals(userId)) {
            throw new ForbiddenException("无权查询其他用户的订单");
        }
        String normalizedStatus = status == null || status.isBlank() ? null : OrderStatus.parse(status).name();
        return orderService.queryByUser(userId, normalizedStatus, effectiveLimit);
    }

    @GovernedTool
    @McpTool(name = "get_order_detail", description = "查询订单详情，包含订单明细",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false))
    public OrderDetail getOrderDetail(
            @McpToolParam(description = "订单 ID", required = true) String orderId) {
        return orderService.getDetail(ownershipValidator.checkOrderOwnership(requireOrder(orderId)));
    }

    @GovernedTool
    @McpTool(name = "update_order_status", description = "更新订单状态（需要 WRITE 权限）",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = true,
                    idempotentHint = true, openWorldHint = false))
    public UpdateResult updateOrderStatus(
            @McpToolParam(description = "订单 ID", required = true) String orderId,
            @McpToolParam(description = "新状态：PENDING/PAID/SHIPPED/COMPLETED", required = true) String newStatus,
            @McpToolParam(description = "备注", required = false) String remark) {
        Order order = ownershipValidator.checkOrderOwnership(requireOrder(orderId));
        return orderService.updateStatus(order, newStatus, remark);
    }

    private Order requireOrder(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("orderId 不能为空");
        }
        Order order = orderService.findByOrderId(orderId);
        if (order == null) {
            throw new NotFoundException("订单不存在：" + orderId);
        }
        return order;
    }
}
