package com.loopers.product.application;

public class ProductCommand {

    public record Create(
        Long brandId,
        String name,
        String description,
        long price,
        int initialStockQuantity
    ) {
    }

    public record Update(
        Long productId,
        String name,
        String description,
        long price
    ) {
    }
}
