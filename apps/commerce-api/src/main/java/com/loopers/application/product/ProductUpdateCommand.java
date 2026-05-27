package com.loopers.application.product;

public record ProductUpdateCommand(
    String name,
    int price
) {}
