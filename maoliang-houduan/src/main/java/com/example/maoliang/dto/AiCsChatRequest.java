package com.example.maoliang.dto;

public record AiCsChatRequest(
        Long conversationId,
        String message,
        String guestToken
) {}
