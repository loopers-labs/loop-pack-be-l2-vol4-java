package com.loopers.product.application;

public record CreateProductCommand(
    Long brandId,
    String name,
    String description,
    long price,
    int stockQuantity
) {}
