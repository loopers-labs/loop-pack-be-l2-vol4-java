package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductInfo;

import java.util.List;

public class ProductV1Dto {

    public record ProductResponse(
            Long id,
            String name,
            String status,
            Long brandId,
            String brandName,
            Long minPrice,
            long likeCount
    ) {
        public static ProductResponse from(ProductInfo info) {
            return new ProductResponse(
                    info.id(), info.name(), info.status(),
                    info.brandId(), info.brandName(), info.minPrice(), info.likeCount()
            );
        }

        public static List<ProductResponse> from(List<ProductInfo> infoList) {
            return infoList.stream().map(ProductResponse::from).toList();
        }
    }

    public record ProductDetailResponse(
            Long id,
            String name,
            String status,
            Long brandId,
            String brandName,
            Long minPrice,
            long likeCount,
            List<StockResponse> stocks
    ) {
        public static ProductDetailResponse from(ProductInfo info) {
            return new ProductDetailResponse(
                    info.id(), info.name(), info.status(),
                    info.brandId(), info.brandName(), info.minPrice(), info.likeCount(),
                    info.stocks().stream().map(StockResponse::from).toList()
            );
        }
    }

    public record StockResponse(Long id, Long price, Integer quantity) {
        public static StockResponse from(ProductInfo.StockInfo stock) {
            return new StockResponse(stock.id(), stock.price(), stock.quantity());
        }
    }
}