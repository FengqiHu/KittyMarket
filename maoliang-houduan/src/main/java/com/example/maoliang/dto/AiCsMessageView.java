package com.example.maoliang.dto;

import java.util.List;

public record AiCsMessageView(
        long id,
        long conversationId,
        String role,
        String content,
        String intent,
        List<AiCsRecommendedProduct> recommendations,
        List<AiCsOrderSummary> orders,
        String provider,
        String createdAt
) {}
