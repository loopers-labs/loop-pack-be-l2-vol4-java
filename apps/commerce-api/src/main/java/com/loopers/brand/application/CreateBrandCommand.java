package com.loopers.brand.application;

public record CreateBrandCommand(
    String name,
    String description
) {
}
