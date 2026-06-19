package com.loopers.domain.product;

/**
 * 상품 목록 정렬 기준.
 * - LATEST: 최신순 (필수)
 * - PRICE_ASC: 가격 오름차순
 * - LIKES_DESC: 좋아요 수 내림차순 (좋아요는 집계로 계산되므로 조합 시점에 정렬)
 */
public enum ProductSortType {
    LATEST,
    PRICE_ASC,
    LIKES_DESC;

    public static ProductSortType from(String value) {
        if (value == null || value.isBlank()) {
            return LATEST;
        }
        return switch (value) {
            case "price_asc" -> PRICE_ASC;
            case "likes_desc" -> LIKES_DESC;
            default -> LATEST;
        };
    }
}
