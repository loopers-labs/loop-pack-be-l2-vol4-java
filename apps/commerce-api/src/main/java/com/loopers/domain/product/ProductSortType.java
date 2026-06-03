package com.loopers.domain.product;

/**
 * 상품 목록 정렬 기준 (UC-03). LATEST가 기본. 모든 정렬은 id DESC tiebreaker로 안정성을 보장한다(01 §7.2).
 */
public enum ProductSortType {
    LATEST,
    PRICE_ASC,
    PRICE_DESC,
    LIKES_DESC
}
