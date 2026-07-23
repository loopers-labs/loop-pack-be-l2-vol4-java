package com.loopers.interfaces.api.ranking;

import com.loopers.application.product.ProductInfo;
import com.loopers.application.ranking.RankingInfo;

public final class RankingDto {
    private RankingDto() {
    }

    public record Response(
        long rank,
        double score,
        Product product
    ) {
        public static Response from(RankingInfo info) {
            return new Response(info.rank(), info.score(), Product.from(info.product()));
        }
    }

    public record Product(
        Long id,
        Brand brand,
        String name,
        String description,
        Long price,
        Integer stock,
        Integer likeCount
    ) {
        private static Product from(ProductInfo info) {
            return new Product(
                info.id(),
                new Brand(info.brand().id(), info.brand().name(), info.brand().description()),
                info.name(),
                info.description(),
                info.price(),
                info.stock(),
                info.likeCount()
            );
        }
    }

    public record Brand(Long id, String name, String description) {
    }
}
