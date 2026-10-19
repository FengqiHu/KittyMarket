package com.example.maoliang.dto;

public record AiCsOrderSummary(
        int orderid,
        int goodid,
        String goodname,
        String picture,
        double price,
        int number,
        int orderstate,
        String statusText,
        String recipientname,
        String telephone,
        String address,
        boolean canCancel,
        boolean canConfirm
) {}
