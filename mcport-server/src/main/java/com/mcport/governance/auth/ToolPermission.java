package com.mcport.governance.auth;

import java.util.Map;

/**
 * 工具名到所需权限的映射。未登记的工具默认按 READ 处理。
 */
public final class ToolPermission {

    private static final Map<String, String> REQUIRED = Map.of(
            "update_order_status", ApiKeyPrincipal.WRITE,
            "lock_inventory", ApiKeyPrincipal.WRITE);

    private ToolPermission() {
    }

    public static String of(String toolName) {
        return REQUIRED.getOrDefault(toolName, ApiKeyPrincipal.READ);
    }
}
