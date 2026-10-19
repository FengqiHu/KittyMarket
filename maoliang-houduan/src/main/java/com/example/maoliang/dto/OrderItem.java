package com.example.maoliang.dto;

public record OrderItem(
        int goodid,
        String goodname,
        double price,
        int number,
        String picture
) {}

