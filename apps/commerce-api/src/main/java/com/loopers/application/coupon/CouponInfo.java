package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.UserCouponModel;

import java.time.ZonedDateTime;

public record CouponInfo(
        Long userCouponId,
        Long couponId,
        CouponStatus status,
        ZonedDateTime expiredAt
) {
    /** 발급 직후 등 — 저장된 status 그대로 */
    public static CouponInfo from(UserCouponModel uc) {
        return new CouponInfo(uc.getId(), uc.getCouponId(), uc.getStatus(), uc.getExpiredAt());
    }

    /** 목록 조회 — AVAILABLE 이어도 만료시각 지났으면 EXPIRED 로 파생 표시 */
    public static CouponInfo from(UserCouponModel uc, ZonedDateTime now) {
        CouponStatus display = uc.getStatus();
        if (display == CouponStatus.AVAILABLE && uc.isExpired(now)) {
            display = CouponStatus.EXPIRED;
        }
        return new CouponInfo(uc.getId(), uc.getCouponId(), display, uc.getExpiredAt());
    }
}
