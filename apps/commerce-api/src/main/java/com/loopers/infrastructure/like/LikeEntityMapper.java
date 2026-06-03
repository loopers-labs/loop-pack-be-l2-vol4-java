package com.loopers.infrastructure.like;

import com.loopers.domain.like.LikeModel;

/**
 * LikeModel(순수 도메인) ↔ LikeEntity(JPA) 변환기. 도메인과 영속 경계 사이의 번역만 담당한다.
 * soft delete 상태(deletedAt)는 도메인과 엔티티(BaseEntity)를 양방향으로 동기화한다.
 */
public final class LikeEntityMapper {

    private LikeEntityMapper() {}

    public static LikeEntity toEntity(LikeModel like) {
        return new LikeEntity(like.getUserId(), like.getProductId(), like.getLikedAt());
    }

    public static LikeModel toDomain(LikeEntity entity) {
        return LikeModel.reconstitute(
                entity.getId(),
                entity.getUserId(),
                entity.getProductId(),
                entity.getLikedAt(),
                entity.getDeletedAt()
        );
    }
}
