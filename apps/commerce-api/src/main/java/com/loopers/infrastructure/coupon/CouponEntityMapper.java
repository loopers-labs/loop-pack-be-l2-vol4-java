package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponModel;

/**
 * CouponModel(순수 도메인) ↔ CouponEntity(JPA) 변환기. soft delete 상태는 양방향 동기화한다.
 */
public final class CouponEntityMapper {

    private CouponEntityMapper() {}

    public static CouponEntity toEntity(CouponModel coupon) {
        return new CouponEntity(
                coupon.getName(),
                coupon.getType(),
                coupon.getValue(),
                coupon.getMinOrderAmount(),
                coupon.getExpiredAt()
        );
    }

    public static CouponModel toDomain(CouponEntity entity) {
        return CouponModel.reconstitute(
                entity.getId(),
                entity.getName(),
                entity.getType(),
                entity.getValue(),
                entity.getMinOrderAmount(),
                entity.getExpiredAt(),
                entity.getDeletedAt()
        );
    }
}
