package com.loopers.application.brand;

public class BrandCommand {
    public record Create(String name, String description) {}
    public record Update(Long brandId, String name, String description) {}
}
