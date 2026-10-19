package com.example.maoliang.dto;

import java.util.List;

public record CreateCartOrderData(List<Item> items, String buyerName, String telephone, String address) {
    public record Item(Integer goodid, Integer number) {}
}
