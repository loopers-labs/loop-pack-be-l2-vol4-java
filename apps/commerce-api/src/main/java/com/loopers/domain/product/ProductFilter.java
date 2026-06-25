package com.loopers.domain.product;

public record ProductFilter(Long brandId, Long minPrice, Long maxPrice, boolean inStock) {

    public static ProductFilter of(Long brandId, Long minPrice, Long maxPrice, Boolean inStock) {
        return new ProductFilter(brandId, minPrice, maxPrice, Boolean.TRUE.equals(inStock));
    }
}
