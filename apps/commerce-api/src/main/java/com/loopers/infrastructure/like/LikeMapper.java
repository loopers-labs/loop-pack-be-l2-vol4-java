package com.loopers.infrastructure.like;

import com.loopers.domain.like.LikeEntity;

public class LikeMapper {

    public static LikeJpaEntity toJpaEntity(LikeEntity entity) {
        return new LikeJpaEntity(
                entity.getId(),
                entity.getUserId(),
                entity.getProductId(),
                entity.getDeletedAt()
        );
    }

    public static LikeEntity toDomain(LikeJpaEntity entity) {
        return LikeEntity.of(
                entity.getId(),
                entity.getUserId(),
                entity.getProductId(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getDeletedAt()
        );
    }
}
