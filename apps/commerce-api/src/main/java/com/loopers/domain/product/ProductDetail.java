package com.loopers.domain.product;

import java.math.BigDecimal;

public record ProductDetail(
    Long id,
    String brandName,
    String name,
    BigDecimal price,
    Long likeCount
) {
}
