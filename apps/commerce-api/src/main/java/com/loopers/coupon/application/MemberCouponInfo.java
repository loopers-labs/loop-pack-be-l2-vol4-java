package com.loopers.coupon.application;

import com.loopers.coupon.domain.Coupon;
import com.loopers.coupon.domain.CouponState;
import com.loopers.coupon.domain.CouponType;
import com.loopers.coupon.domain.MemberCoupon;

import java.time.ZonedDateTime;

public record MemberCouponInfo(
    Long memberCouponId,
    Long memberId,
    Long couponId,
    String couponName,
    CouponType type,
    Long value,
    Long minOrderAmount,
    CouponState state,
    ZonedDateTime issuedAt,
    ZonedDateTime usedAt,
    ZonedDateTime expiredAt) {

    /** 발급 쿠폰 + 템플릿 정보를 조합한다. 상태는 {@code now} 기준으로 만료를 반영해 표시한다. */
    public static MemberCouponInfo of(MemberCoupon memberCoupon, Coupon coupon, ZonedDateTime now) {
        return new MemberCouponInfo(
            memberCoupon.getId(),
            memberCoupon.getMemberId(),
            memberCoupon.getCouponId(),
            coupon != null ? coupon.getName() : null,
            coupon != null ? coupon.getType() : null,
            coupon != null ? coupon.getValue() : null,
            coupon != null ? coupon.getMinOrderAmount() : null,
            memberCoupon.currentState(now),
            memberCoupon.getIssuedAt(),
            memberCoupon.getUsedAt(),
            memberCoupon.getExpiredAt());
    }
}
