package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductEntity;

public class ProductMapper {

    public static ProductJpaEntity toJpaEntity(ProductEntity entity) {
        return new ProductJpaEntity(
                entity.getId(),
                entity.getBrandId(),
                entity.getName(),
                entity.getDescription(),
                entity.getPrice(),
                entity.getLikeCount(),
                entity.getDeletedAt()
        );
    }

    public static ProductEntity toDomain(ProductJpaEntity entity) {
        return ProductEntity.of(
                entity.getId(),
                entity.getBrandId(),
                entity.getName(),
                entity.getDescription(),
                entity.getPrice(),
                entity.getLikeCount(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getDeletedAt()
        );
    }
}
