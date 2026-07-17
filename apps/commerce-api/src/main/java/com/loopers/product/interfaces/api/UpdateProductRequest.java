package com.loopers.product.interfaces.api;

public record UpdateProductRequest(String name, String description, Long price, Integer stock) {}
