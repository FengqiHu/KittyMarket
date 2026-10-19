package com.example.maoliang.dto;

public record AiCsRecommendedProduct(
        int goodid,
        String goodname,
        String kind,
        String subkind,
        double price,
        String picture,
        String description,
        int number,
        String catkind,
        String catage,
        String catweight,
        String matchReason
) {}
