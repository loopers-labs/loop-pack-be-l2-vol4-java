package com.loopers.application.brand;

public record UpdateBrandCommand(
    Long brandId,
    String name,
    String description
) {
}
