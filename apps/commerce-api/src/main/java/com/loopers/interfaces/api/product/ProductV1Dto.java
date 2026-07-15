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
        Long likeCount,
        Integer rank
    ) {
        /** 목록 등 순위가 없는 응답용 — rank 는 null. */
        public static ProductResponse from(ProductInfo info) {
            return from(info, null);
        }

        /** 상세 응답용 — 실시간 랭킹(ZREVRANK)에서 병합한 순위(1-indexed, 없으면 null)를 함께 싣는다. */
        public static ProductResponse from(ProductInfo info, Integer rank) {
            return new ProductResponse(
                info.id(),
                info.brandId(),
                info.brandName(),
                info.name(),
                info.description(),
                info.price(),
                info.inStock(),
                info.remainingStock(),
                info.likeCount(),
                rank
            );
        }
    }
}
