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

    /**
     * 문자열 → enum 매핑. 대소문자 무관 ("LIKES_DESC", "likes_desc" 둘 다 같은 값).
     * 알 수 없는 값은 LATEST 로 폴백.
     */
    public static ProductSortType from(String value) {
        if (value == null || value.isBlank()) {
            return LATEST;
        }
        return switch (value.toLowerCase()) {
            case "price_asc" -> PRICE_ASC;
            case "likes_desc" -> LIKES_DESC;
            case "latest" -> LATEST;
            default -> LATEST;
        };
    }
}
