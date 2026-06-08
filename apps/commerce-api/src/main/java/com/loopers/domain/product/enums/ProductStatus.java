package com.loopers.domain.product.enums;

public enum ProductStatus {
    ACTIVE("활성화"),
    INACTIVE("비활성화");

    private final String description;

    ProductStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
