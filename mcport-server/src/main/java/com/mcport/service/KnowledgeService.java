package com.mcport.service;

import com.mcport.dto.KnowledgeDocument;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/**
 * 知识库检索：阶段 2 返回固定文档，阶段 4 有余力再接 Elasticsearch。
 */
@Service
public class KnowledgeService {

    private static final List<KnowledgeDocument> DOCUMENTS = List.of(
            new KnowledgeDocument("doc-001", "订单状态说明",
                    "订单状态依次为 PENDING（待支付）、PAID（已支付）、SHIPPED（已发货）、COMPLETED（已完成）。"
                            + "已完成的订单不可回退，已发货订单只能流转到已完成。",
                    "order", 0.92),
            new KnowledgeDocument("doc-002", "库存锁定规则",
                    "库存分为可用库存与预留库存。下单锁定会从可用库存扣减并计入预留库存，"
                            + "取消订单时按锁定流水回补。同一订单对同一 SKU 重复锁定按幂等处理。",
                    "inventory", 0.88),
            new KnowledgeDocument("doc-003", "退款与售后时效",
                    "已支付订单在发货前可申请退款，退款审核通过后 1-3 个工作日原路退回；"
                            + "已发货订单需走退货流程，签收后 7 天内可申请。",
                    "after-sales", 0.81));

    public List<KnowledgeDocument> search(String query, String category) {
        String keyword = query == null ? "" : query.toLowerCase(Locale.ROOT);
        return DOCUMENTS.stream()
                .filter(doc -> category == null || category.isBlank() || doc.category().equals(category))
                .filter(doc -> keyword.isBlank()
                        || doc.title().toLowerCase(Locale.ROOT).contains(keyword)
                        || doc.content().toLowerCase(Locale.ROOT).contains(keyword)
                        || doc.category().equalsIgnoreCase(keyword))
                .toList();
    }
}
