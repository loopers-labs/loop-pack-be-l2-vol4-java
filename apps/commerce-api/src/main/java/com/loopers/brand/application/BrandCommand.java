package com.loopers.brand.application;

public class BrandCommand {

    public record Create(String name, String description, String logoUrl) {
    }

    public record Update(Long brandId, String name, String description, String logoUrl) {
    }
}
