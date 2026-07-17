package com.loopers.product.interfaces.api;

public record CreateProductRequest(
    Long brandId, String name, String description, Long price, Integer stock) {}
