package com.example.maoliang.dto;

import com.example.maoliang.Entity.Good;

import java.util.List;

public record GoodPage(List<Good> items, long total) {
}
