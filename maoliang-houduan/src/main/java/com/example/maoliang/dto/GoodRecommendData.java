package com.example.maoliang.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
public class GoodRecommendData {
    private int goodid;
    private String goodname;
    private double price;
    private String picture;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Double weight;

    public GoodRecommendData(int goodid, String goodname, double price, String picture) {
        this(goodid, goodname, price, picture, null);
    }

    public GoodRecommendData(int goodid, String goodname, double price, String picture, Double weight) {
        this.goodid = goodid;
        this.goodname = goodname;
        this.price = price;
        this.picture = picture;
        this.weight = weight;
    }
}
