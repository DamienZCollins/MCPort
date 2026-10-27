package com.mcport.governance.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcport.governance.audit.AuditService;
import com.mcport.governance.auth.ApiKeyContext;
import com.mcport.governance.auth.ApiKeyPrincipal;
import com.mcport.governance.auth.ToolPermissionRegistry;
import com.mcport.governance.metrics.GovernanceMetrics;
import com.mcport.governance.ratelimit.RedisRateLimiter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class McpToolGuardFilterTest {

    private final RedisRateLimiter rateLimiter = mock(RedisRateLimiter.class);
    private final AuditService auditService = mock(AuditService.class);
    private final GovernanceMetrics metrics = mock(GovernanceMetrics.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private McpToolGuardFilter filter;

    @BeforeEach
    void setUp() {
        ApplicationContext context = mock(ApplicationContext.class);
        when(context.getBeanDefinitionNames()).thenReturn(new String[]{"sample"});
        doReturn(SampleTools.class).when(context).getType("sample");
        ToolPermissionRegistry registry = new ToolPermissionRegistry(context);
        registry.afterSingletonsInstantiated();
        filter = new McpToolGuardFilter(rateLimiter, auditService, metrics, registry, objectMapper);
    }

    @AfterEach
    void tearDown() {
        ApiKeyContext.clear();
    }

    @Test
    void allowsRegisteredReadToolForReadKey() throws Exception {
        authenticate(List.of("READ"));
        MockHttpServletResponse response = filter(callBody("query_orders"));

        assertThat(response.getStatus()).isEqualTo(200);
        verify(rateLimiter).checkLimit(anyString(), anyInt());
    }

    @Test
    void rejectsWriteToolForReadKeyWith403() throws Exception {
        authenticate(List.of("READ"));
        MockHttpServletResponse response = filter(callBody("update_order_status"));

        assertThat(response.getStatus()).isEqualTo(403);
        verify(rateLimiter, never()).checkLimit(anyString(), anyInt());
        verify(metrics).recordBlock("forbidden");
    }

    @Test
    void rejectsUnregisteredToolInsteadOfDefaultingToRead() throws Exception {
        authenticate(List.of("READ", "WRITE"));
        MockHttpServletResponse response = filter(callBody("brand_new_tool"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString(StandardCharsets.UTF_8)).contains("未登记权限");
    }

    @Test
    void letsProtocolMethodsThroughWithoutRateLimit() throws Exception {
        authenticate(List.of("READ"));
        MockHttpServletResponse response = filter("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}");

        assertThat(response.getStatus()).isEqualTo(200);
        verify(rateLimiter, never()).checkLimit(anyString(), anyInt());
    }

    @Test
    void returns429WhenRateLimited() throws Exception {
        authenticate(List.of("READ"));
        doThrow(new com.mcport.governance.exception.RateLimitException("限流"))
                .when(rateLimiter).checkLimit(anyString(), anyInt());

        MockHttpServletResponse response = filter(callBody("query_orders"));

        assertThat(response.getStatus()).isEqualTo(429);
        verify(metrics).recordBlock("rate_limit");
    }

    @Test
    void trailingSlashIsStillGoverned() throws Exception {
        authenticate(List.of("READ"));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mcp/");
        request.setContent(callBody("update_order_status").getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(403);
    }

    private void authenticate(List<String> permissions) {
        ApiKeyContext.set(new ApiKeyPrincipal("key", "user0", permissions, 60));
    }

    private MockHttpServletResponse filter(String body) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mcp");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    private String callBody(String toolName) {
        return "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\""
                + toolName + "\",\"arguments\":{}}}";
    }

    static class SampleTools {

        @com.mcport.governance.aspect.GovernedTool(permission = ApiKeyPrincipal.READ)
        @org.springaicommunity.mcp.annotation.McpTool(name = "query_orders", description = "sample")
        public String queryOrders() {
            return "[]";
        }

        @com.mcport.governance.aspect.GovernedTool(permission = ApiKeyPrincipal.WRITE)
        @org.springaicommunity.mcp.annotation.McpTool(name = "update_order_status", description = "sample")
        public String updateOrderStatus() {
            return "{}";
        }
    }
}
