package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.UserCoupon;

/**
 * 발급 응답 전용 DTO. 발급 확인에 필요한 최소 정보만 담는다.
 * 템플릿 상세는 필요 없으므로 UserCoupon 만으로 조립한다.
 */
public record IssuedCouponInfo(
    Long id,
    Long couponId,
    CouponStatus status
) {
    public static IssuedCouponInfo from(UserCoupon userCoupon) {
        return new IssuedCouponInfo(
            userCoupon.getId(),
            userCoupon.getCouponId(),
            userCoupon.getStatus()
        );
    }
}
