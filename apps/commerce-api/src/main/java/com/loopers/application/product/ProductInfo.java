package com.loopers.application.product;

import com.loopers.domain.product.ProductModel;

/**
 * 상품 등록·단건 응답. 재고는 독립 Aggregate(StockModel)에서 조회해 외부 인자로 주입한다.
 */
public record ProductInfo(
    Long id,
    Long brandId,
    String name,
    String description,
    String imageUrl,
    Long price,
    Integer stock,
    Long likesCount
) {
    public static ProductInfo of(ProductModel product, int stock) {
        return new ProductInfo(
            product.getId(),
            product.getBrandId(),
            product.getName(),
            product.getDescription(),
            product.getImageUrl(),
            product.getPrice(),
            stock,
            product.getLikesCount()
        );
    }
}
