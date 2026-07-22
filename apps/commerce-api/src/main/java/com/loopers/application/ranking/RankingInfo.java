package com.loopers.application.ranking;

import com.loopers.domain.product.ProductModel;

/**
 * 랭킹 페이지의 한 줄(응용 DTO). ZSET 의 순위·점수에 DB 에서 hydrate 한 상품정보를 붙인다.
 */
public record RankingInfo(long rank, RankedProduct product, double score) {

    public record RankedProduct(Long id, String name, Long price, long likeCount, Long brandId) {
        public static RankedProduct from(ProductModel product) {
            return new RankedProduct(
                product.getId(),
                product.getName(),
                product.getPrice(),
                product.getLikeCount(),
                product.getBrandId()
            );
        }
    }

    public static RankingInfo of(long rank, ProductModel product, double score) {
        return new RankingInfo(rank, RankedProduct.from(product), score);
    }
}
