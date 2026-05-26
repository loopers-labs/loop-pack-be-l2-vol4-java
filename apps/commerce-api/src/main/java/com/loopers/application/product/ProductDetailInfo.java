package com.loopers.application.product;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductDetail;

/**
 * 상품 상세/목록 응답용 정보. 브랜드 정보와 좋아요 수를 함께 포함한다.
 */
public record ProductDetailInfo(
    Long id,
    String name,
    String description,
    Long price,
    Integer stock,
    Long brandId,
    String brandName,
    long likeCount
) {
    public static ProductDetailInfo from(ProductDetail detail) {
        Product product = detail.product();
        Brand brand = detail.brand();
        return new ProductDetailInfo(
            product.getId(),
            product.getName(),
            product.getDescription(),
            product.getPrice().amount().longValue(),
            product.getStock(),
            brand.getId(),
            brand.getName(),
            detail.likeCount()
        );
    }
}
