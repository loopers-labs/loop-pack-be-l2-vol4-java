package com.loopers.domain.product;

public enum SortType {
    LATEST,     // createdAt DESC (기본값)
    PRICE_ASC,  // price ASC
    LIKES_DESC; // likeCount DESC

    public static SortType from(String value) {
        if (value == null) return LATEST;
        return switch (value.toLowerCase()) {
            case "price_asc" -> PRICE_ASC;
            case "likes_desc" -> LIKES_DESC;
            default -> LATEST;
        };
    }
}
