package com.loopers.domain.product.projection;

public record ProductDetail(
    Long productId,
    String name,
    String description,
    Long brandId,
    String brandName,
    Integer price,
    Boolean isAvailable,
    Integer likeCount
) {

    public ProductDetail(Long productId, String name, String description, Long brandId, String brandName, Integer price, Integer stock, Integer likeCount) {
        this(productId, name, description, brandId, brandName, price, stock > 0, likeCount);
    }
}
