package com.mcport.governance.filter;

import jakarta.servlet.http.HttpServletRequest;

/**
 * MCP 端点的路径判定。鉴权与治理过滤器共用，避免两处各写一份导致漏拦截。
 *
 * <p>比较前去掉 context path、矩阵参数（{@code ;...}）与尾部斜杠，
 * 防止 {@code /mcp/} 这类等价路径绕过鉴权。
 */
public final class McpPaths {

    private static final String MCP_PATH = "/mcp";

    private McpPaths() {
    }

    public static boolean isMcpEndpoint(HttpServletRequest request) {
        String path = request.getRequestURI();
        String context = request.getContextPath();
        if (context != null && !context.isEmpty() && path.startsWith(context)) {
            path = path.substring(context.length());
        }
        int semicolon = path.indexOf(';');
        if (semicolon >= 0) {
            path = path.substring(0, semicolon);
        }
        while (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return MCP_PATH.equals(path);
    }
}
