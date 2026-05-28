package com.loopers.infrastructure.brand;

import com.loopers.domain.brand.BrandEntity;

public class BrandMapper {

    public static BrandJpaEntity toJpaEntity(BrandEntity entity) {
        return new BrandJpaEntity(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getDeletedAt()
        );
    }

    public static BrandEntity toDomain(BrandJpaEntity entity) {
        return BrandEntity.of(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getDeletedAt()
        );
    }
}
