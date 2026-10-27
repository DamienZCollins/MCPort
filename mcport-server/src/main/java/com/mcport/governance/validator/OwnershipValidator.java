package com.mcport.governance.validator;

import com.mcport.entity.Order;
import com.mcport.governance.auth.ApiKeyContext;
import com.mcport.governance.auth.ApiKeyPrincipal;
import com.mcport.governance.exception.ForbiddenException;
import com.mcport.governance.exception.NotFoundException;
import org.springframework.stereotype.Component;

/**
 * 越权校验：普通调用方只能访问自己的订单，管理员放行。
 */
@Component
public class OwnershipValidator {

    public Order checkOrderOwnership(Order order) {
        if (order == null) {
            throw new NotFoundException("订单不存在");
        }
        ApiKeyPrincipal principal = ApiKeyContext.require();
        if (principal.isAdmin()) {
            return order;
        }
        if (!principal.userId().equals(order.getUserId())) {
            throw new ForbiddenException("无权访问其他用户的订单");
        }
        return order;
    }
}
