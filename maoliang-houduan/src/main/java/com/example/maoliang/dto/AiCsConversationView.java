package com.example.maoliang.dto;

public record AiCsConversationView(
        long id,
        String title,
        String difyConversationId,
        String createdAt,
        String updatedAt,
        String lastMessage,
        int messageCount
) {}
