package com.mcport.governance.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcport.governance.audit.AuditService;
import com.mcport.governance.exception.ForbiddenException;
import com.mcport.governance.exception.UnauthorizedException;
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
 * 校验 /mcp 请求的 Bearer API Key，通过后把调用方写入 {@link ApiKeyContext}。
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 20)
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final String MCP_PATH = "/mcp";
    private static final String BEARER_PREFIX = "Bearer ";

    private final ApiKeyService apiKeyService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public ApiKeyAuthFilter(ApiKeyService apiKeyService, AuditService auditService, ObjectMapper objectMapper) {
        this.apiKeyService = apiKeyService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !MCP_PATH.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            ApiKeyPrincipal principal = apiKeyService.authenticate(extractApiKey(request));
            ApiKeyContext.set(principal);
            chain.doFilter(request, response);
        } catch (UnauthorizedException e) {
            auditService.logBlock("unknown", maskedApiKey(request), "unknown", e, 0);
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized", e.getMessage());
        } catch (ForbiddenException e) {
            auditService.logBlock("unknown", maskedApiKey(request), "unknown", e, 0);
            writeError(response, HttpServletResponse.SC_FORBIDDEN, "Forbidden", e.getMessage());
        } finally {
            ApiKeyContext.clear();
        }
    }

    /**
     * 无效凭证不落完整 Key，只留前 4 位便于排查。
     */
    private String maskedApiKey(HttpServletRequest request) {
        String key = extractApiKey(request);
        if (key == null || key.isBlank()) {
            return "none";
        }
        return key.length() <= 4 ? "****" : key.substring(0, 4) + "****";
    }

    private String extractApiKey(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        return header.substring(BEARER_PREFIX.length()).trim();
    }

    private void writeError(HttpServletResponse response, int status, String error, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), Map.of("error", error, "message", message));
    }
}
