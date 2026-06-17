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
            detail.product().getLikesCount(),
            detail.brand().getId(),
            detail.brand().getName()
        );
    }

    /** 캐시된 사용자 무관 데이터에 사용자별 liked를 합쳐 최종 응답 DTO를 만든다. */
    public ProductDetailInfo toInfo(boolean liked) {
        return new ProductDetailInfo(id, name, description, imageUrl, price, inStock, likesCount, brandId, brandName, liked);
    }
}
