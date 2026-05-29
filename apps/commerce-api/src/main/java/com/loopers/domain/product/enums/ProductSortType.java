package com.loopers.domain.product.enums;

public enum ProductSortType {
    LATEST("최신순"),
    PRICE_ASC("가격 낮은순"),
    LIKES_DESC("찜 많은순");

    private final String description;

    ProductSortType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public static ProductSortType from(String value) {
        return switch (value.toLowerCase()) {
            case "price_asc" -> PRICE_ASC;
            case "likes_desc" -> LIKES_DESC;
            default -> LATEST;
        };
    }
}