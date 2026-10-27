package com.mcport.client;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 一个 API Key 对应一条 Streamable HTTP 会话。
 *
 * <p>鉴权头在传输层注入：MCP SDK 不会把自定义请求头带进协议方法里。
 */
final class Session implements AutoCloseable {

    private final McpSyncClient client;

    private Session(McpSyncClient client) {
        this.client = client;
    }

    /**
     * @param apiKey 为 null 或空串时不带 Authorization 头，用于验证 401
     */
    static Session open(String endpoint, String apiKey) {
        HttpClientStreamableHttpTransport.Builder builder = HttpClientStreamableHttpTransport.builder(endpoint)
                .connectTimeout(Duration.ofSeconds(5));
        if (apiKey != null && !apiKey.isBlank()) {
            builder.customizeRequest(request -> request.header("Authorization", "Bearer " + apiKey));
        }
        McpSyncClient client = McpClient.sync(builder.build())
                .clientInfo(new McpSchema.Implementation("mcport-java-client", "1.0.0"))
                .requestTimeout(Duration.ofSeconds(15))
                .build();
        client.initialize();
        return new Session(client);
    }

    McpSchema.Implementation serverInfo() {
        return client.getServerInfo();
    }

    String protocolVersion() {
        McpSchema.InitializeResult result = client.getCurrentInitializationResult();
        return result == null ? "unknown" : result.protocolVersion();
    }

    List<McpSchema.Tool> listTools() {
        return client.listTools().tools();
    }

    /**
     * 调用工具，区分两类失败：工具内业务错误是 {@code isError:true} 的正常响应，
     * HTTP 层拦截（401/403/429）则以运输层异常抛出。
     */
    ToolCall call(String tool, Map<String, Object> arguments) {
        long start = System.nanoTime();
        try {
            McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest(tool, arguments));
            return new ToolCall(tool, elapsedMs(start), Boolean.TRUE.equals(result.isError()),
                    textOf(result), 0);
        } catch (RuntimeException e) {
            return new ToolCall(tool, elapsedMs(start), true, rootMessage(e), httpStatusOf(e));
        }
    }

    @Override
    public void close() {
        client.closeGracefully();
    }

    /**
     * SDK 把失败响应整体塞进异常信息，状态码本身不一定出现；
     * 服务端错误体固定为 {@code {"error":"Unauthorized|Forbidden|Rate Limit Exceeded"}}，按它判定。
     *
     * @return 未在 HTTP 层被拦截时为 0
     */
    static int httpStatusOf(Throwable e) {
        String message = rootMessage(e);
        if (message.contains("\"Unauthorized\"") || message.contains("缺少或无效")) {
            return 401;
        }
        if (message.contains("\"Forbidden\"") || message.contains("API Key 已禁用")) {
            return 403;
        }
        if (message.contains("\"Rate Limit Exceeded\"")) {
            return 429;
        }
        return 0;
    }

    static String rootMessage(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private static String textOf(McpSchema.CallToolResult result) {
        StringBuilder text = new StringBuilder();
        for (McpSchema.Content content : result.content()) {
            if (content instanceof McpSchema.TextContent textContent) {
                text.append(textContent.text());
            }
        }
        return text.toString();
    }
}
