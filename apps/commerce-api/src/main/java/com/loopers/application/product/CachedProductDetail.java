package com.loopers.application.product;

import com.loopers.domain.product.ProductDetail;

/**
 * 상품 상세의 <b>사용자 무관</b> 캐시 표현 (Redis 직렬화 대상). {@link ProductDetailInfo}에서 사용자별
 * 좋아요 여부(liked)만 뺀 것 — 전 사용자가 캐시 1벌을 공유하고, liked는 Facade가 조회 후 {@link #toInfo}로 덧붙인다.
 * 도메인 모델(ProductModel 등) 대신 평탄한 record라 JSON 직렬화/역직렬화가 단순하다.
 */
public record CachedProductDetail(
    Long id,
    String name,
    String description,
    String imageUrl,
    Long price,
    boolean inStock,
    Long likesCount,
    Long brandId,
    String brandName
) {
    public static CachedProductDetail from(ProductDetail detail) {
        return new CachedProductDetail(
            detail.product().getId(),
            detail.product().getName(),
            detail.product().getDescription(),
            detail.product().getImageUrl(),
            detail.product().getPrice(),
            detail.stockQuantity() > 0,
            detail.likeCount(),
            detail.brand().getId(),
            detail.brand().getName()
        );
    }

    /**
     * 캐시된 사용자 무관 데이터에 사용자별 liked + 실시간 rank(오늘 랭킹 순위, 없으면 null)를 합쳐 최종 응답 DTO를 만든다.
     * rank는 캐시에 담지 않는다(실시간 값이라 매 조회 Facade가 조합).
     */
    // 원본 보존(week8 랭킹 rank 추가 전):
    // public ProductDetailInfo toInfo(boolean liked) {
    //     return new ProductDetailInfo(id, name, description, imageUrl, price, inStock, likesCount, brandId, brandName, liked);
    // }
    // week9 최초: toInfo(boolean liked, Long rank) — 오늘 순위만 덧붙였다. rankYesterday 는 추세 노출로 추가.
    public ProductDetailInfo toInfo(boolean liked, Long rank, Long rankYesterday) {
        return new ProductDetailInfo(id, name, description, imageUrl, price, inStock, likesCount, brandId, brandName, liked, rank, rankYesterday);
    }
}
