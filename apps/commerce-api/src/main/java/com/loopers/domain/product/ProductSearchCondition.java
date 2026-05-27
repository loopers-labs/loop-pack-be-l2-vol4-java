package com.loopers.domain.product;

/**
 * 상품 목록 조회 조건.
 * - brandId: 브랜드 필터 (null이면 전체)
 * - sortType: 정렬 조건 (null이면 LATEST 기본값 적용)
 */
public record ProductSearchCondition(Long brandId, SortType sortType) {

    public static ProductSearchCondition of(Long brandId, SortType sortType) {
        return new ProductSearchCondition(
            brandId,
            sortType != null ? sortType : SortType.LATEST
        );
    }
}
