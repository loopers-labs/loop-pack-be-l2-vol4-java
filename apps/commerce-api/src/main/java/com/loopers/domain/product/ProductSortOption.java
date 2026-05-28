package com.loopers.domain.product;

/**
 * 상품 목록 조회 정렬 옵션.
 * 도메인이 정렬 의도를 알고 있고, 인프라는 이를 구체 정렬 스펙으로 변환한다.
 */
public enum ProductSortOption {
    LATEST,       // 최신순 (createdAt DESC)
    PRICE_ASC,    // 가격 낮은순
    LIKES_DESC    // 좋아요 많은순
}
