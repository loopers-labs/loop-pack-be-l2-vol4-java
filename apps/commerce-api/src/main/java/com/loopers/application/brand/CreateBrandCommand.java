package com.loopers.application.brand;

public record CreateBrandCommand(
    String name,
    String description
) {
}
