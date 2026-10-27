package com.mcport.service;

import com.mcport.dto.KnowledgeDocument;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeServiceTest {

    private final KnowledgeService service = new KnowledgeService();

    @Test
    void searchMatchesContentKeyword() {
        List<KnowledgeDocument> docs = service.search("库存", null);

        assertThat(docs).extracting(KnowledgeDocument::id).containsExactly("doc-002");
    }

    @Test
    void searchFiltersByCategory() {
        List<KnowledgeDocument> docs = service.search("订单", "order");

        assertThat(docs).hasSize(1);
        assertThat(docs.get(0).category()).isEqualTo("order");
    }

    @Test
    void searchWithoutMatchReturnsEmpty() {
        assertThat(service.search("不存在的关键词", null)).isEmpty();
    }
}
