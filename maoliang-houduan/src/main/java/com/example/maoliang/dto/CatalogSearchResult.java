package com.example.maoliang.dto;

import com.example.maoliang.Entity.Good;
import java.util.List;

public record CatalogSearchResult(List<Good> items, String summary, String mode, List<Integer> sourceIds) {}
