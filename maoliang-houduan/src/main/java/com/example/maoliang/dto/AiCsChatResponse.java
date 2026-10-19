package com.example.maoliang.dto;

public record AiCsChatResponse(
        AiCsConversationView conversation,
        AiCsMessageView userMessage,
        AiCsMessageView assistantMessage
) {}
