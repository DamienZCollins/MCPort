package com.mcport.governance.aspect;

import com.mcport.governance.audit.AuditService;
import com.mcport.governance.auth.ApiKeyContext;
import com.mcport.governance.auth.ApiKeyPrincipal;
import com.mcport.governance.exception.ForbiddenException;
import com.mcport.governance.metrics.GovernanceMetrics;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springaicommunity.mcp.annotation.McpTool;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class McpToolGovernanceAspectTest {

    private final AuditService auditService = mock(AuditService.class);
    private final GovernanceMetrics metrics = mock(GovernanceMetrics.class);
    private final McpToolGovernanceAspect aspect = new McpToolGovernanceAspect(auditService, metrics);
    private final SampleTools target = new SampleTools();
    private final ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
    private final MethodSignature signature = mock(MethodSignature.class);

    @BeforeEach
    void setUp() throws Exception {
        ApiKeyContext.set(new ApiKeyPrincipal("key", "user0", List.of("READ"), 60));
        Method method = SampleTools.class.getMethod("run", String.class);
        when(pjp.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        when(pjp.getTarget()).thenReturn(target);
        when(pjp.getArgs()).thenReturn(new Object[]{"x"});
    }

    @AfterEach
    void tearDown() {
        ApiKeyContext.clear();
    }

    @Test
    void recordsSuccessWithResolvedToolName() throws Throwable {
        when(pjp.proceed()).thenReturn("ok");

        Object result = aspect.govern(pjp, governed());

        assertThat(result).isEqualTo("ok");
        verify(metrics).recordSuccess(eq("query_orders"), anyLong());
        verify(auditService).logSuccess(eq("query_orders"), eq("key"), eq("user0"),
                any(), eq("ok"), anyLong());
    }

    @Test
    void recordsViolationAsBlocked() throws Throwable {
        when(pjp.proceed()).thenThrow(new ForbiddenException("无权访问"));

        assertThatThrownBy(() -> aspect.govern(pjp, governed()))
                .isInstanceOf(ForbiddenException.class);

        verify(metrics).recordBlocked("query_orders");
        verify(metrics).recordBlock("unauthorized_access");
        verify(auditService).logBlock(eq("query_orders"), eq("key"), eq("user0"), any(), anyLong());
        verify(metrics, never()).recordSuccess(anyString(), anyLong());
    }

    @Test
    void recordsUnexpectedError() throws Throwable {
        when(pjp.proceed()).thenThrow(new IllegalStateException("boom"));

        assertThatThrownBy(() -> aspect.govern(pjp, governed()))
                .isInstanceOf(IllegalStateException.class);

        verify(metrics).recordFailure(eq("query_orders"), eq("error"), anyLong());
        verify(auditService).logFailure(eq("query_orders"), eq("key"), eq("user0"), any(), any(), anyLong());
    }

    private GovernedTool governed() throws NoSuchMethodException {
        return SampleTools.class.getMethod("run", String.class).getAnnotation(GovernedTool.class);
    }

    static class SampleTools {

        @GovernedTool
        @McpTool(name = "query_orders", description = "sample")
        public String run(String input) {
            return input;
        }
    }
}
