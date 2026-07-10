package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductInfo;
import com.loopers.application.product.StockInfo;

public class ProductV1Dto {

    public record StockResponse(Long productId, boolean inStock, Integer remainingStock) {
        public static StockResponse from(StockInfo info) {
            return new StockResponse(info.productId(), info.inStock(), info.remainingStock());
        }
    }

    public record ProductResponse(
        Long id,
        Long brandId,
        String brandName,
        String name,
        String description,
        Long price,
        boolean inStock,
        Integer remainingStock,
        Long likeCount
    ) {
        public static ProductResponse from(ProductInfo info) {
            return new ProductResponse(
                info.id(),
                info.brandId(),
                info.brandName(),
                info.name(),
                info.description(),
                info.price(),
                info.inStock(),
                info.remainingStock(),
                info.likeCount()
            );
        }
    }
}
