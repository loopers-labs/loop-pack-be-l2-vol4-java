package com.loopers.domain.product.projection;

public record ProductSummary(
    Long productId,
    String name,
    Long brandId,
    String brandName,
    Integer price,
    Boolean isAvailable,
    Integer likeCount
) {

    public ProductSummary(Long productId, String name, Long brandId, String brandName, Integer price, Integer stock, Integer likeCount) {
        this(productId, name, brandId, brandName, price, stock > 0, likeCount);
    }
}
