package com.loopers.application.ranking;

import com.loopers.application.product.ProductInfo;

import java.time.LocalDate;
import java.util.List;

/**
 * 랭킹 페이지 조회 결과 — 상품 ID가 아닌 상품정보(ProductInfo)가 Aggregation 되어 담긴다.
 * rank는 ZSET 상의 절대 순위(1-based) — 삭제된 상품이 목록에서 빠져도 남은 상품의 순위 번호는 유지된다.
 */
public record RankingPageInfo(
    LocalDate date,
    int page,
    int size,
    long totalCount,
    List<RankedProduct> items
) {
    public record RankedProduct(long rank, ProductInfo product) {}
}
