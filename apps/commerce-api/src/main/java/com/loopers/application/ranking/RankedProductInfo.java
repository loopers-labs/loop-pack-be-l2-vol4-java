package com.loopers.application.ranking;

/**
 * 랭킹 한 항목의 응용 결과 — 순위 + 상품 요약(이름/가격/브랜드/좋아요수) + 스코어를 Facade 가 조합한 값.
 * 순위(rank)와 스코어(score)는 랭킹 도메인에서, 나머지 상품 요약은 Product/Brand/Metrics 에서 온다.
 */
public record RankedProductInfo(
        long rank,
        Long productId,
        String name,
        Long price,
        Long brandId,
        String brandName,
        Long likesCount,
        double score
) {
}
