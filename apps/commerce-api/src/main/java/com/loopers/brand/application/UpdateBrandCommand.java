package com.loopers.brand.application;

public record UpdateBrandCommand(
    Long brandId,
    String name,
    String description
) {
}
