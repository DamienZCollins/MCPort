package com.mcport.tool;

import com.mcport.dto.KnowledgeDocument;
import com.mcport.governance.aspect.GovernedTool;
import com.mcport.governance.auth.ApiKeyPrincipal;
import com.mcport.service.KnowledgeService;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class KnowledgeTools {

    private final KnowledgeService knowledgeService;

    public KnowledgeTools(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @GovernedTool(permission = ApiKeyPrincipal.READ)
    @McpTool(name = "search_knowledge", description = "检索业务知识库（订单、库存、售后规则）",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false))
    public List<KnowledgeDocument> searchKnowledge(
            @McpToolParam(description = "检索关键词", required = true) String query,
            @McpToolParam(description = "分类：order/inventory/after-sales，留空返回全部", required = false) String category) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query 不能为空");
        }
        return knowledgeService.search(query, category);
    }
}
