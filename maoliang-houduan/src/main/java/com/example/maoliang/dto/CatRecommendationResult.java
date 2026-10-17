package com.example.maoliang.dto;

import java.util.List;

public record CatRecommendationResult(int catid, String catname, List<GoodRecommendData> items,
                                      String summary, String mode, List<Integer> sourceIds) {}
