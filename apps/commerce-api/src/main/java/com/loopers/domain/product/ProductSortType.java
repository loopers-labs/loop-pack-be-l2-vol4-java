package com.loopers.domain.product;

public enum ProductSortType {
    /** 최신순 (createdAt DESC) */
    LATEST,
    /** 가격 오름차순 */
    PRICE_ASC,
    /** 좋아요 많은 순 (denormalized like_count DESC) */
    LIKES_DESC,
}
