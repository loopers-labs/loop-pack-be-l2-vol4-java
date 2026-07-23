package com.loopers.application.ranking;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.product.Product;

public final class RankingInfo {

    private RankingInfo() {
    }

    /** 랭킹 페이지의 한 항목 — 상품 ID 가 아닌 상품 정보로 조립(aggregation)해 반환한다. */
    public record RankedItem(
            long rank,
            Long productId,
            Long brandId,
            String brandName,
            String name,
            long price,
            long likeCount,
            double score
    ) {

        public static RankedItem from(long rank, double score, Product product, Brand brand) {
            return new RankedItem(
                    rank,
                    product.getId(),
                    brand.getId(),
                    brand.getName(),
                    product.getName(),
                    product.getPrice().getAmount(),
                    product.getLikeCount(),
                    score
            );
        }
    }
}
