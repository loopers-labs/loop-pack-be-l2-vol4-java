package com.loopers.tddstudy.domain.product;

public record ProductDetail(
        Long productId,
        String productName,
        int price,
        int stock,
        long likeCount,
        String status,
        Long brandId,
        String brandName
) {}
