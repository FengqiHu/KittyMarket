package com.example.maoliang.dto;

public record AiCsOrderActionRequest(
        Long conversationId,
        Integer orderId,
        String action,
        String guestToken
) {}
