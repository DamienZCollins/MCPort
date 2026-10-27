package com.mcport.governance.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class GovernanceMetrics {

    private final MeterRegistry meterRegistry;

    public GovernanceMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordSuccess(String toolName, long latencyMs) {
        meterRegistry.counter("mcp.tool.calls", "tool", toolName, "status", "success").increment();
        recordLatency(toolName, latencyMs);
    }

    public void recordFailure(String toolName, String status, long latencyMs) {
        meterRegistry.counter("mcp.tool.calls", "tool", toolName, "status", status).increment();
        recordLatency(toolName, latencyMs);
    }

    /**
     * 被拦截的调用没有执行耗时，只计调用数。
     */
    public void recordBlocked(String toolName) {
        meterRegistry.counter("mcp.tool.calls", "tool", toolName, "status", "blocked").increment();
    }

    public void recordBlock(String reason) {
        meterRegistry.counter("mcp.blocks", "reason", reason).increment();
    }

    private void recordLatency(String toolName, long latencyMs) {
        Timer.builder("mcp.tool.latency").tag("tool", toolName).register(meterRegistry)
                .record(latencyMs, TimeUnit.MILLISECONDS);
    }
}
