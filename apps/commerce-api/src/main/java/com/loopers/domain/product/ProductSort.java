package com.loopers.domain.product;

public enum ProductSort {
    LATEST, PRICE_ASC, LIKES_DESC;

    public static ProductSort from(String value) {
        if (value == null) {
            return LATEST;
        }
        return switch (value.toLowerCase()) {
            case "price_asc" -> PRICE_ASC;
            case "likes_desc" -> LIKES_DESC;
            default -> LATEST;
        };
    }
}
