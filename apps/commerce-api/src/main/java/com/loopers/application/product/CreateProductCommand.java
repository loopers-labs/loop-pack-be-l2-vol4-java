package com.loopers.application.product;

public record CreateProductCommand(
    Long brandId,
    String name,
    String description,
    long price,
    int stockQuantity
) {}
