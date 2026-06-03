package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.IssuedCouponView;
import com.loopers.domain.coupon.UserCouponStatus;

import java.time.ZonedDateTime;

/** 발급분 응답용 (내 쿠폰 목록 UC-14, 발급 UC-13, Admin 발급 내역 UC-16). */
public record IssuedCouponInfo(
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
    public static IssuedCouponInfo from(IssuedCouponView view) {
        return new IssuedCouponInfo(
                view.userCouponId(),
                view.userId(),
                view.couponId(),
                view.couponName(),
                view.type(),
                view.value(),
                view.minOrderAmount(),
                view.status(),
                view.issuedAt(),
                view.usedAt(),
                view.expiredAt()
        );
    }
}
