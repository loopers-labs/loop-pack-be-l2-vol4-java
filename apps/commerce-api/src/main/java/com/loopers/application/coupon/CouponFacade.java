package com.loopers.application.coupon;

import com.loopers.domain.coupon.UserCouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/** 대고객 쿠폰 유스케이스 — 발급(UC-13), 내 쿠폰 목록(UC-14). */
@Component
@RequiredArgsConstructor
public class CouponFacade {

    private final UserCouponService userCouponService;

    public IssuedCouponInfo issue(Long userId, Long couponId) {
        return IssuedCouponInfo.from(userCouponService.issue(userId, couponId));
    }

    public List<IssuedCouponInfo> getMyCoupons(Long userId, int page, int size) {
        return userCouponService.getMyCoupons(userId, page, size).stream()
                .map(IssuedCouponInfo::from)
                .toList();
    }
}
