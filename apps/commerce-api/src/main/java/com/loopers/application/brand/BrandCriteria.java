package com.loopers.application.brand;

public final class BrandCriteria {

    private BrandCriteria() {
    }

    public record Register(String name, String description) {
    }

    public record Modify(Long id, String name, String description) {
    }
}
