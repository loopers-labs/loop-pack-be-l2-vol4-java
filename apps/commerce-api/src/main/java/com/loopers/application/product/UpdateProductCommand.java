package com.loopers.application.product;

public record UpdateProductCommand(
    Long productId,
    String name,
    String description,
    long price,
    Integer stockQuantity
) {

    public boolean hasStockQuantity() {
        return stockQuantity != null;
    }
}
