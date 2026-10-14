package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductDetailInfo;
import com.loopers.application.product.ProductInfo;
import com.loopers.domain.product.ProductStatus;

public final class ProductV1Dto {

    private ProductV1Dto() {}

    public record CreateProductRequest(
        Long brandId,
        String name,
        String description,
        Long price,
        Integer stock,
        String imageUrl
    ) {}

    public record ProductResponse(
        Long id,
        Long brandId,
        String name,
        String description,
        Long price,
        Integer stock,
        Long likeCount,
        String imageUrl,
        ProductStatus status
    ) {
        public static ProductResponse from(ProductInfo info) {
            return new ProductResponse(
                info.id(),
                info.brandId(),
                info.name(),
                info.description(),
                info.price(),
                info.stock(),
                info.likeCount(),
                info.imageUrl(),
                info.status()
            );
        }
    }

    public record ProductDetailResponse(
        Long productId,
        String productName,
        String description,
        Long price,
        Integer stock,
        Long likeCount,
        String imageUrl,
        ProductStatus status,
        BrandInfo brand
    ) {
        public record BrandInfo(Long id, String name, String description) {}

        public static ProductDetailResponse from(ProductDetailInfo detail) {
            return new ProductDetailResponse(
                detail.productId(),
                detail.productName(),
                detail.description(),
                detail.price(),
                detail.stock(),
                detail.likeCount(),
                detail.imageUrl(),
                detail.status(),
                new BrandInfo(detail.brandId(), detail.brandName(), detail.brandDescription())
            );
        }
    }
}
