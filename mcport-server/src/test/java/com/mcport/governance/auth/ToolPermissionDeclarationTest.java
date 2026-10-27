package com.mcport.governance.auth;

import com.mcport.governance.aspect.GovernedTool;
import org.junit.jupiter.api.Test;
import org.springaicommunity.mcp.annotation.McpTool;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 反射校验所有 {@code @McpTool} 方法都显式声明了权限，防止新增工具漏配。
 */
class ToolPermissionDeclarationTest {

    private static final List<Class<?>> TOOL_CLASSES = List.of(
            com.mcport.tool.OrderTools.class,
            com.mcport.tool.InventoryTools.class,
            com.mcport.tool.KnowledgeTools.class);

    @Test
    void everyMcpToolDeclaresPermission() {
        for (Class<?> type : TOOL_CLASSES) {
            for (Method method : type.getMethods()) {
                McpTool tool = method.getAnnotation(McpTool.class);
                if (tool == null) {
                    continue;
                }
                GovernedTool governed = method.getAnnotation(GovernedTool.class);
                assertThat(governed)
                        .as("%s#%s 缺少 @GovernedTool", type.getSimpleName(), method.getName())
                        .isNotNull();
                assertThat(governed.permission())
                        .as("%s#%s 权限", type.getSimpleName(), method.getName())
                        .isIn(ApiKeyPrincipal.READ, ApiKeyPrincipal.WRITE);
            }
        }
    }

    @Test
    void writeToolsAreMarkedWrite() {
        assertThat(permissionOf("update_order_status")).isEqualTo(ApiKeyPrincipal.WRITE);
        assertThat(permissionOf("lock_inventory")).isEqualTo(ApiKeyPrincipal.WRITE);
    }

    @Test
    void readToolsAreMarkedRead() {
        assertThat(permissionOf("query_orders")).isEqualTo(ApiKeyPrincipal.READ);
        assertThat(permissionOf("get_order_detail")).isEqualTo(ApiKeyPrincipal.READ);
        assertThat(permissionOf("check_inventory")).isEqualTo(ApiKeyPrincipal.READ);
        assertThat(permissionOf("search_knowledge")).isEqualTo(ApiKeyPrincipal.READ);
    }

    private String permissionOf(String toolName) {
        for (Class<?> type : TOOL_CLASSES) {
            for (Method method : type.getMethods()) {
                McpTool tool = method.getAnnotation(McpTool.class);
                if (tool != null && tool.name().equals(toolName)) {
                    return method.getAnnotation(GovernedTool.class).permission();
                }
            }
        }
        throw new AssertionError("未找到工具：" + toolName);
    }
}
