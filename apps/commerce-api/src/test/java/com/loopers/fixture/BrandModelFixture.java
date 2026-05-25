package com.loopers.fixture;

import com.loopers.domain.brand.BrandModel;

public class BrandModelFixture {

    private String name = "나이키";
    private String description = "글로벌 스포츠 브랜드";

    public static BrandModelFixture aBrand() {
        return new BrandModelFixture();
    }

    public BrandModelFixture withName(String name) { this.name = name; return this; }
    public BrandModelFixture withDescription(String description) { this.description = description; return this; }

    public BrandModel build() {
        return new BrandModel(name, description);
    }
}
