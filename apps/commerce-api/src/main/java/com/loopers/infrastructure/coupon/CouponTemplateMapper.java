package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponTemplateEntity;

public class CouponTemplateMapper {

    public static CouponTemplateJpaEntity toJpaEntity(CouponTemplateEntity entity) {
        return new CouponTemplateJpaEntity(
                entity.getId(),
                entity.getName(),
                entity.getType(),
                entity.getValue(),
                entity.getMinOrderAmount(),
                entity.getExpiredAt(),
                entity.getDeletedAt()
        );
    }

    public static CouponTemplateEntity toDomain(CouponTemplateJpaEntity entity) {
        return CouponTemplateEntity.of(
                entity.getId(),
                entity.getName(),
                entity.getType(),
                entity.getValue(),
                entity.getMinOrderAmount(),
                entity.getExpiredAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getDeletedAt()
        );
    }
}
