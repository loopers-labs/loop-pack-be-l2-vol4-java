package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductAdminInfo;
import com.loopers.application.product.ProductInfo;

import java.math.BigDecimal;

public class ProductAdminV1Dto {

    public record ProductResponse(
        Long id,
        String brandName,
        String name,
        BigDecimal price,
        Long likeCount
    ) {
        public static ProductResponse from(ProductInfo info) {
            return new ProductResponse(
                info.id(),
                info.brandName(),
                info.name(),
                info.price(),
                info.likeCount()
            );
        }
    }

    public record ProductDetailResponse(
        Long id,
        Long brandId,
        String brandName,
        String name,
        BigDecimal price,
        Long likeCount,
        Long stock
    ) {
        public static ProductDetailResponse from(ProductAdminInfo info) {
            return new ProductDetailResponse(
                info.id(),
                info.brandId(),
                info.brandName(),
                info.name(),
                info.price(),
                info.likeCount(),
                info.stock()
            );
        }
    }

    public record CreateProductRequest(
        Long brandId,
        String name,
        BigDecimal price,
        Long stock
    ) {}

    public record UpdateProductRequest(
        String name,
        BigDecimal price,
        Long stock
    ) {}
}
