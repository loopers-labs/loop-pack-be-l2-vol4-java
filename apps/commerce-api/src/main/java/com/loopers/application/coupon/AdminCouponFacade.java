package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.IssuedCouponView;
import com.loopers.domain.coupon.UserCouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * Admin 쿠폰 유스케이스 — 템플릿 CRUD(UC-15), 발급 내역 조회(UC-16).
 * (현 프로젝트에 권한 체계가 없어 운영자 인가는 미적용 — 브랜드·상품 관리와 동일, 운영 시 인가 선행 필요.)
 */
@Component
@RequiredArgsConstructor
public class AdminCouponFacade {

    private final CouponService couponService;
    private final UserCouponService userCouponService;

    public CouponInfo register(String name, CouponType type, long value, Long minOrderAmount, ZonedDateTime expiredAt) {
        return CouponInfo.from(couponService.register(name, type, value, minOrderAmount, expiredAt));
    }

    public CouponInfo getCoupon(Long couponId) {
        return CouponInfo.from(couponService.getCoupon(couponId));
    }

    public List<CouponInfo> getCoupons(int page, int size) {
        return couponService.getCoupons(page, size).stream().map(CouponInfo::from).toList();
    }

    public CouponInfo update(Long couponId, String name, long value, Long minOrderAmount, ZonedDateTime expiredAt) {
        return CouponInfo.from(couponService.update(couponId, name, value, minOrderAmount, expiredAt));
    }

    public void deleteCoupon(Long couponId) {
        couponService.deleteCoupon(couponId);
    }

    /** 특정 템플릿의 발급 내역 — 발급분 목록을 템플릿과 조합해 상태를 파생한다(UC-16). */
    public List<IssuedCouponInfo> getIssues(Long couponId, int page, int size) {
        CouponModel coupon = couponService.getCoupon(couponId);
        ZonedDateTime now = ZonedDateTime.now();
        return userCouponService.getIssues(couponId, page, size).stream()
                .map(uc -> IssuedCouponInfo.from(IssuedCouponView.of(uc, coupon, now)))
                .toList();
    }
}
