package com.loopers.product.interfaces;

import com.loopers.product.application.ProductInfo;
import com.loopers.product.application.ProductSummaryInfo;

public class ProductV1Dto {

    public record ProductSummaryResponse(Long id, String name, Long price, Integer stock, Long brandId, String brandName, Long likeCount) {
        public static ProductSummaryResponse from(ProductSummaryInfo info) {
            return new ProductSummaryResponse(
                info.id(),
                info.name(),
                info.price(),
                info.stock(),
                info.brandId(),
                info.brandName(),
                info.likeCount()
            );
        }
    }

    public record ProductResponse(Long id, String name, String description, Long price, Integer stock, Long brandId, String brandName, Long likeCount) {
        public static ProductResponse from(ProductInfo info) {
            return new ProductResponse(
                info.id(),
                info.name(),
                info.description(),
                info.price(),
                info.stock(),
                info.brandId(),
                info.brandName(),
                info.likeCount()
            );
        }
    }
}
