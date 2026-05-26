package com.loopers.infrastructure.brand;

import com.loopers.domain.brand.BrandModel;

/**
 * BrandModel(순수 도메인) ↔ BrandEntity(JPA) 변환기. 도메인과 영속 경계 사이의 번역만 담당한다.
 * soft delete 상태는 도메인의 deletedAt과 엔티티(BaseEntity)의 deletedAt을 양방향으로 동기화한다.
 */
public final class BrandEntityMapper {

    private BrandEntityMapper() {}

    public static BrandEntity toEntity(BrandModel brand) {
        return new BrandEntity(brand.getName(), brand.getDescription());
    }

    public static BrandModel toDomain(BrandEntity entity) {
        return BrandModel.reconstitute(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getDeletedAt()
        );
    }
}
