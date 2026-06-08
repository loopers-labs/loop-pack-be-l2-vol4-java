package com.loopers.interfaces.api.product;

import java.math.BigDecimal;

public class ProductAdminDto {
    public record RegisterProductRequest(
            Long brandId,
            String name,
            BigDecimal price,
            int initialStock
    ) {}

    public record UpdateProductRequest(
            String name,
            BigDecimal price
    ) {}
}
