package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.UserCouponModel;

/**
 * UserCouponModel(순수 도메인) ↔ UserCouponEntity(JPA) 변환기.
 * version은 영속 관심사라 도메인에 노출하지 않고 엔티티(@Version)에만 둔다.
 */
public final class UserCouponEntityMapper {

    private UserCouponEntityMapper() {}

    public static UserCouponEntity toEntity(UserCouponModel userCoupon) {
        return new UserCouponEntity(
                userCoupon.getUserId(),
                userCoupon.getCouponId(),
                userCoupon.getUsedAt(),
                userCoupon.getIssuedAt()
        );
    }

    public static UserCouponModel toDomain(UserCouponEntity entity) {
        return UserCouponModel.reconstitute(
                entity.getId(),
                entity.getUserId(),
                entity.getCouponId(),
                entity.getUsedAt(),
                entity.getIssuedAt()
        );
    }
}
