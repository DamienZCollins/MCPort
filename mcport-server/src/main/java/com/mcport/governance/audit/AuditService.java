package com.mcport.governance.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcport.entity.AuditLog;
import com.mcport.governance.exception.ForbiddenException;
import com.mcport.governance.exception.RateLimitException;
import com.mcport.governance.exception.UnauthorizedException;
import com.mcport.mapper.AuditLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 审计日志：异步写入 MySQL，失败只记录日志，不影响工具调用结果。
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private static final int SUMMARY_MAX = 500;
    private static final int PARAMS_MAX = 2000;

    private final AuditLogMapper auditLogMapper;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogMapper auditLogMapper, ObjectMapper objectMapper) {
        this.auditLogMapper = auditLogMapper;
        this.objectMapper = objectMapper;
    }

    @Async("auditExecutor")
    public void logSuccess(String toolName, String apiKey, String userId, Object[] args, Object result,
                           long latencyMs) {
        AuditLog entry = base(toolName, apiKey, userId, latencyMs);
        entry.setInputParams(params(args));
        entry.setStatus("SUCCESS");
        entry.setOutputSummary(truncate(toJson(result), SUMMARY_MAX));
        persist(entry);
    }

    @Async("auditExecutor")
    public void logFailure(String toolName, String apiKey, String userId, Object[] args, Throwable error,
                           long latencyMs) {
        AuditLog entry = base(toolName, apiKey, userId, latencyMs);
        entry.setInputParams(params(args));
        entry.setStatus("ERROR");
        entry.setErrorMessage(truncate(error.getMessage(), 500));
        persist(entry);
    }

    /**
     * 拦截日志：权限不足、限流、无效凭证。由过滤器在进入 MCP 协议前调用。
     */
    @Async("auditExecutor")
    public void logBlock(String toolName, String apiKey, String userId, Throwable error, long latencyMs) {
        AuditLog entry = base(toolName, apiKey, userId, latencyMs);
        entry.setStatus("BLOCKED");
        entry.setBlockReason(blockReason(error));
        entry.setErrorMessage(truncate(error.getMessage(), 500));
        persist(entry);
    }

    private static String blockReason(Throwable error) {
        if (error instanceof RateLimitException) {
            return "RATE_LIMIT";
        }
        if (error instanceof ForbiddenException) {
            return "FORBIDDEN";
        }
        if (error instanceof UnauthorizedException) {
            return "UNAUTHORIZED";
        }
        return "INVALID_PARAM";
    }

    private AuditLog base(String toolName, String apiKey, String userId, long latencyMs) {
        AuditLog entry = new AuditLog();
        entry.setRequestId(UUID.randomUUID().toString());
        entry.setToolName(toolName == null ? "unknown" : toolName);
        entry.setApiKey(apiKey == null ? "unknown" : apiKey);
        entry.setUserId(userId == null ? "unknown" : userId);
        entry.setLatencyMs((int) Math.min(latencyMs, Integer.MAX_VALUE));
        return entry;
    }

    private void persist(AuditLog entry) {
        try {
            auditLogMapper.insert(entry);
        } catch (RuntimeException e) {
            log.warn("写入审计日志失败 tool={} status={}：{}", entry.getToolName(), entry.getStatus(), e.getMessage());
        }
    }

    private String params(Object[] args) {
        return args == null ? null : truncate(toJson(args), PARAMS_MAX);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max);
    }
}
