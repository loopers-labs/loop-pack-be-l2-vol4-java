package com.loopers.domain.product.projection;

import java.time.ZonedDateTime;

public record ProductAdminView(
    Long productId,
    String name,
    String description,
    Long brandId,
    String brandName,
    Integer price,
    Integer stock,
    ZonedDateTime createdAt,
    ZonedDateTime updatedAt
) {
}
