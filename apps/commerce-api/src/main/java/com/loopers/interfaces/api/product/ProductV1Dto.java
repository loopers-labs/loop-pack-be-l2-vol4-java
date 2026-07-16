package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductDetailInfo;
import com.loopers.application.product.ProductInfo;

import java.math.BigDecimal;

public class ProductV1Dto {
    public record ProductResponse(
        Long id,
        String brandName,
        String name,
        BigDecimal price,
        Long likeCount,
        boolean inStock,
        Long rank
    ) {
        public static ProductResponse from(ProductInfo info) {
            return new ProductResponse(
                info.id(),
                info.brandName(),
                info.name(),
                info.price(),
                info.likeCount(),
                info.inStock(),
                null
            );
        }

        public static ProductResponse from(ProductDetailInfo detail) {
            ProductInfo info = detail.product();
            return new ProductResponse(
                info.id(),
                info.brandName(),
                info.name(),
                info.price(),
                info.likeCount(),
                info.inStock(),
                detail.rank()
            );
        }
    }
}
