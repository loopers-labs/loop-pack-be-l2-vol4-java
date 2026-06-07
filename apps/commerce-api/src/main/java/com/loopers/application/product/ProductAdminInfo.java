package com.loopers.application.product;

import java.time.ZonedDateTime;

import com.loopers.domain.product.projection.ProductAdminView;

public record ProductAdminInfo(
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

    public static ProductAdminInfo from(ProductAdminView productAdminView) {
        return new ProductAdminInfo(
            productAdminView.productId(),
            productAdminView.name(),
            productAdminView.description(),
            productAdminView.brandId(),
            productAdminView.brandName(),
            productAdminView.price(),
            productAdminView.stock(),
            productAdminView.createdAt(),
            productAdminView.updatedAt()
        );
    }
}
