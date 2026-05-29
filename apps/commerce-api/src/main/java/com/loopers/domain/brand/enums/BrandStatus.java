package com.loopers.domain.brand.enums;

public enum BrandStatus {
    ACTIVE("활성화"),
    INACTIVE("비활성화");

    private final String description;

    BrandStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
