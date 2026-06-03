package com.loopers.application.catalog.brand;

public class BrandCommand {
    public record Create(
        String name,
        String description
    ) {}

    public record Update(
        String name,
        String description
    ) {}
}
