package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponEntity;

public class CouponMapper {

    public static CouponJpaEntity toJpaEntity(CouponEntity entity) {
        return new CouponJpaEntity(
                entity.getId(),
                entity.getCouponTemplateId(),
                entity.getUserId(),
                entity.getStatus(),
                entity.getDeletedAt()
        );
    }

    public static CouponEntity toDomain(CouponJpaEntity entity) {
        return CouponEntity.of(
                entity.getId(),
                entity.getCouponTemplateId(),
                entity.getUserId(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getDeletedAt()
        );
    }
}
