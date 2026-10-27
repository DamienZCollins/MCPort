package com.mcport.tool;

import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * MCP Server 集成验证用的最小工具，阶段 2 由业务工具替换。
 */
@Component
public class HelloTools {

    @McpTool(name = "hello", description = "测试工具：返回问候语")
    public String hello(
            @McpToolParam(description = "名字", required = true) String name) {
        return "Hello, " + name;
    }
}
