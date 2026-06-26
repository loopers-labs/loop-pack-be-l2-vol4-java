package com.loopers.application.coupon;

import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.UserCoupon;

import java.time.LocalDateTime;

/**
 * 발급된 쿠폰 응답 모델.
 * displayStatus 는 UserCoupon.status(AVAILABLE/USED) 와 Coupon.expiredAt 을 함께 보고 계산한다.
 * 따라서 EXPIRED 는 DB 상태가 아니라 조회 시 계산 결과.
 */
public record UserCouponInfo(
    Long userCouponId,
    Long couponId,
    String couponName,
    String couponType,
    Long discountValue,
    Long minOrderAmount,
    String displayStatus, // AVAILABLE | USED | EXPIRED
    LocalDateTime expiredAt,
    LocalDateTime issuedAt,
    LocalDateTime usedAt
) {
    public static UserCouponInfo from(UserCoupon userCoupon, Coupon coupon) {
        return new UserCouponInfo(
            userCoupon.getId(),
            coupon.getId(),
            coupon.getName(),
            coupon.getType().name(),
            coupon.getValue(),
            coupon.getMinOrderAmount(),
            computeDisplayStatus(userCoupon, coupon),
            coupon.getExpiredAt(),
            userCoupon.getIssuedAt(),
            userCoupon.getUsedAt()
        );
    }

    private static String computeDisplayStatus(UserCoupon userCoupon, Coupon coupon) {
        if (userCoupon.getStatus() == CouponStatus.USED) {
            return "USED";
        }
        if (coupon.isExpired(LocalDateTime.now())) {
            return "EXPIRED";
        }
        return "AVAILABLE";
    }
}
