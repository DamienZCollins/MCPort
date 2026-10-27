package com.mcport.governance.aspect;

import com.mcport.governance.audit.AuditService;
import com.mcport.governance.auth.ApiKeyContext;
import com.mcport.governance.auth.ApiKeyPrincipal;
import com.mcport.governance.exception.ForbiddenException;
import com.mcport.governance.metrics.GovernanceMetrics;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springframework.aop.support.AopUtils;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * 工具执行切面：审计与指标。
 *
 * <p>权限校验与限流放在 {@link com.mcport.governance.filter.McpToolGuardFilter}，
 * 因为它们需要返回真实的 403 / 429；工具方法内抛出的异常会被 MCP SDK 包成
 * {@code isError:true} 的工具结果，拿不到 HTTP 状态码。
 */
@Aspect
@Component
public class McpToolGovernanceAspect {

    private final AuditService auditService;
    private final GovernanceMetrics metrics;

    public McpToolGovernanceAspect(AuditService auditService, GovernanceMetrics metrics) {
        this.auditService = auditService;
        this.metrics = metrics;
    }

    @Around("@annotation(governedTool)")
    public Object govern(ProceedingJoinPoint pjp, GovernedTool governedTool) throws Throwable {
        String toolName = resolveToolName(pjp);
        ApiKeyPrincipal principal = ApiKeyContext.require();
        long start = System.currentTimeMillis();

        try {
            Object result = pjp.proceed();
            long latency = System.currentTimeMillis() - start;
            metrics.recordSuccess(toolName, latency);
            auditService.logSuccess(toolName, principal.apiKey(), principal.userId(),
                    pjp.getArgs(), result, latency);
            return result;
        } catch (ForbiddenException e) {
            // 权限与限流在过滤器拦截；能走到这里的 403 只来自越权校验
            long latency = System.currentTimeMillis() - start;
            metrics.recordBlocked(toolName);
            metrics.recordBlock("unauthorized_access");
            auditService.logBlock(toolName, principal.apiKey(), principal.userId(), e, latency);
            throw e;
        } catch (Throwable e) {
            long latency = System.currentTimeMillis() - start;
            metrics.recordFailure(toolName, "error", latency);
            auditService.logFailure(toolName, principal.apiKey(), principal.userId(),
                    pjp.getArgs(), e, latency);
            throw e;
        }
    }

    private String resolveToolName(ProceedingJoinPoint pjp) {
        Method method = ((MethodSignature) pjp.getSignature()).getMethod();
        McpTool annotation = AopUtils.getMostSpecificMethod(method, pjp.getTarget().getClass())
                .getAnnotation(McpTool.class);
        return annotation == null ? method.getName() : annotation.name();
    }
}
