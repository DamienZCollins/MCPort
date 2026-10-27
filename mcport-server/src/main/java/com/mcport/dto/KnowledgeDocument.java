package com.mcport.dto;

public record KnowledgeDocument(
        String id,
        String title,
        String content,
        String category,
        double score) {
}
