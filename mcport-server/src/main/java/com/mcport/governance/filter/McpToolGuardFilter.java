package com.mcport.governance.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcport.governance.audit.AuditService;
import com.mcport.governance.auth.ApiKeyContext;
import com.mcport.governance.auth.ApiKeyPrincipal;
import com.mcport.governance.auth.ToolPermission;
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
import java.util.Map;

/**
 * 工具级治理：在 MCP 协议处理之前校验权限与限流，以便返回真实的 403 / 429。
 *
 * <p>工具方法内部抛出的异常会被 MCP SDK 包成 {@code isError:true} 的工具结果，
 * 拿不到 HTTP 状态码，因此权限与限流必须在 HTTP 层拦截。
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class McpToolGuardFilter extends OncePerRequestFilter {

    private static final String MCP_PATH = "/mcp";

    private final RedisRateLimiter rateLimiter;
    private final AuditService auditService;
    private final GovernanceMetrics metrics;
    private final ObjectMapper objectMapper;

    public McpToolGuardFilter(RedisRateLimiter rateLimiter, AuditService auditService,
                              GovernanceMetrics metrics, ObjectMapper objectMapper) {
        this.rateLimiter = rateLimiter;
        this.auditService = auditService;
        this.metrics = metrics;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !MCP_PATH.equals(request.getRequestURI()) || !"POST".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        byte[] body = request.getInputStream().readAllBytes();
        String toolName = extractToolName(body);
        if (toolName == null) {
            chain.doFilter(new CachedBodyRequest(request, body), response);
            return;
        }

        ApiKeyPrincipal principal = ApiKeyContext.require();
        try {
            String required = ToolPermission.of(toolName);
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

    private String extractToolName(byte[] body) {
        if (body == null || body.length == 0) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            if (root.isArray() || !"tools/call".equals(root.path("method").asText())) {
                return null;
            }
            String name = root.path("params").path("name").asText(null);
            return name == null || name.isBlank() ? null : name;
        } catch (IOException e) {
            return null;
        }
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
}
