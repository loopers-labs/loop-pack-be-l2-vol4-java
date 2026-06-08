package com.loopers.application.product;

public record ProductCreateCommand(
    Long brandId,
    String name,
    int price,
    int initialStock
) {}
