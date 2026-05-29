package com.loopers.domain.product;

import java.math.BigDecimal;

public record ProductDetail(
    Long productId,
    String name,
    BigDecimal price,
    Long brandId,
    String brandName,
    int likeCount,
    Integer stockQuantity
) {
}
