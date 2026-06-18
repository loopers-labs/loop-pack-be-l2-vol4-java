package com.loopers.domain.coupon;

import java.time.ZonedDateTime;

/**
 * 내 쿠폰 목록 한 줄 (UC-14). 발급분 + 템플릿 + 파생 상태를 조합한 도메인 읽기 모델.
 * 상태(status)는 used_at + 템플릿 expired_at으로 조회 시점에 파생된다(01 §7.5).
 */
public record IssuedCouponView(
        Long userCouponId,
        Long userId,
        Long couponId,
        String couponName,
        CouponType type,
        long value,
        Long minOrderAmount,
        UserCouponStatus status,
        ZonedDateTime issuedAt,
        ZonedDateTime usedAt,
        ZonedDateTime expiredAt
) {
    public static IssuedCouponView of(UserCouponModel userCoupon, CouponModel coupon, ZonedDateTime now) {
        return new IssuedCouponView(
                userCoupon.getId(),
                userCoupon.getUserId(),
                coupon.getId(),
                coupon.getName(),
                coupon.getType(),
                coupon.getValue(),
                coupon.getMinOrderAmount(),
                userCoupon.resolveStatus(now, coupon.getExpiredAt()),
                userCoupon.getIssuedAt(),
                userCoupon.getUsedAt(),
                coupon.getExpiredAt()
        );
    }
}
