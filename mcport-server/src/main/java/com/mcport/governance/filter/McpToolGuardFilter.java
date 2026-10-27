package com.mcport.governance.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcport.governance.audit.AuditService;
import com.mcport.governance.auth.ApiKeyContext;
import com.mcport.governance.auth.ApiKeyPrincipal;
import com.mcport.governance.auth.ToolPermissionRegistry;
import com.mcport.governance.exception.ForbiddenException;
import com.mcport.governance.exception.RateLimitException;
import com.mcport.governance.metrics.GovernanceMetrics;
import com.mcport.governance.ratelimit.RedisRateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 工具级治理：在 MCP 协议处理之前校验权限与限流，以便返回真实的 403 / 429。
 *
 * <p>工具方法内部抛出的异常会被 MCP SDK 包成 {@code isError:true} 的工具结果，
 * 拿不到 HTTP 状态码，因此权限与限流必须在 HTTP 层拦截。
 *
 * <p>所有无法识别的请求体一律拒绝而非放行：批量数组、非 JSON-RPC 对象、
 * 未登记工具都在此拦住，避免「解析不出来就跳过治理」。
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class McpToolGuardFilter extends OncePerRequestFilter {

    private final RedisRateLimiter rateLimiter;
    private final AuditService auditService;
    private final GovernanceMetrics metrics;
    private final ToolPermissionRegistry toolPermissions;
    private final ObjectMapper objectMapper;

    public McpToolGuardFilter(RedisRateLimiter rateLimiter, AuditService auditService,
                              GovernanceMetrics metrics, ToolPermissionRegistry toolPermissions,
                              ObjectMapper objectMapper) {
        this.rateLimiter = rateLimiter;
        this.auditService = auditService;
        this.metrics = metrics;
        this.toolPermissions = toolPermissions;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !McpPaths.isMcpEndpoint(request) || !"POST".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        byte[] body = request.getInputStream().readAllBytes();
        JsonNode root;
        try {
            root = body.length == 0 ? null : objectMapper.readTree(body);
        } catch (IOException e) {
            writeJsonRpcError(response, HttpServletResponse.SC_BAD_REQUEST, -32700, "Parse error");
            return;
        }

        if (root != null && root.isArray()) {
            // SDK 按单条 JSON-RPC 处理，批量请求无法逐条治理，直接拒绝
            writeJsonRpcError(response, HttpServletResponse.SC_BAD_REQUEST, -32600,
                    "批量请求不受支持，请逐条发送");
            return;
        }

        String toolName = extractToolName(root);
        if (toolName == null) {
            chain.doFilter(new CachedBodyRequest(request, body), response);
            return;
        }

        ApiKeyPrincipal principal = ApiKeyContext.require();
        try {
            String required = toolPermissions.required(toolName).orElseThrow(() -> new ForbiddenException(
                    "工具 " + toolName + " 未登记权限，拒绝调用"));
            if (!principal.hasPermission(required)) {
                throw new ForbiddenException("工具 " + toolName + " 需要 " + required + " 权限");
            }
            rateLimiter.checkLimit(principal.apiKey(), principal.rateLimitPerMinute());
        } catch (ForbiddenException | RateLimitException e) {
            metrics.recordBlocked(toolName);
            metrics.recordBlock(e instanceof RateLimitException ? "rate_limit" : "forbidden");
            auditService.logBlock(toolName, principal.apiKey(), principal.userId(), e, 0);
            writeError(response, e);
            return;
        }

        chain.doFilter(new CachedBodyRequest(request, body), response);
    }

    private String extractToolName(JsonNode root) {
        if (root == null || !root.isObject() || !"tools/call".equals(root.path("method").asText())) {
            return null;
        }
        String name = root.path("params").path("name").asText(null);
        return name == null || name.isBlank() ? null : name;
    }

    private void writeError(HttpServletResponse response, RuntimeException e) throws IOException {
        boolean rateLimited = e instanceof RateLimitException;
        response.setStatus(rateLimited ? 429 : 403);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), Map.of(
                "error", rateLimited ? "Rate Limit Exceeded" : "Forbidden",
                "message", e.getMessage()));
    }

    private void writeJsonRpcError(HttpServletResponse response, int status, int code, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jsonrpc", "2.0");
        body.put("id", null);
        body.put("error", Map.of("code", code, "message", message));
        objectMapper.writeValue(response.getWriter(), body);
    }
}
