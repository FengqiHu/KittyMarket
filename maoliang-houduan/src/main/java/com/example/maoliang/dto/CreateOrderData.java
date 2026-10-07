package com.example.maoliang.dto;

public record CreateOrderData(Integer goodid, Integer number, String buyerName,
                              String telephone, String address, Boolean fromCart) {
}
