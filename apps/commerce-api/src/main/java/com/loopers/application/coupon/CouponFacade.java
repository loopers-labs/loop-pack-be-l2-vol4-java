package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.CouponType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;

@RequiredArgsConstructor
@Component
public class CouponFacade {

    private final CouponService couponService;

    @Transactional
    public CouponInfo.Template createCoupon(
        String name,
        CouponType type,
        Long value,
        Long minOrderAmount,
        ZonedDateTime expiredAt
    ) {
        return CouponInfo.Template.from(couponService.createCoupon(name, type, value, minOrderAmount, expiredAt));
    }

    @Transactional(readOnly = true)
    public CouponInfo.Template getCoupon(Long id) {
        return CouponInfo.Template.from(couponService.getCoupon(id));
    }

    @Transactional(readOnly = true)
    public List<CouponInfo.Template> getCoupons(Integer page, Integer size) {
        return couponService.getCoupons(page, size).stream()
            .map(CouponInfo.Template::from)
            .toList();
    }

    @Transactional
    public CouponInfo.Template updateCoupon(
        Long id,
        String name,
        CouponType type,
        Long value,
        Long minOrderAmount,
        ZonedDateTime expiredAt
    ) {
        return CouponInfo.Template.from(couponService.updateCoupon(id, name, type, value, minOrderAmount, expiredAt));
    }

    @Transactional
    public void deleteCoupon(Long id) {
        couponService.deleteCoupon(id);
    }

    @Transactional
    public CouponInfo.Issued issueCoupon(Long couponId, String userLoginId, ZonedDateTime now) {
        return CouponInfo.Issued.from(couponService.issueCoupon(couponId, userLoginId, now), now);
    }

    @Transactional(readOnly = true)
    public List<CouponInfo.Issued> getMyCoupons(String userLoginId, ZonedDateTime now) {
        return couponService.getIssuedCoupons(userLoginId).stream()
            .map(issuedCoupon -> CouponInfo.Issued.from(issuedCoupon, now))
            .toList();
    }

    @Transactional(readOnly = true)
    public List<CouponInfo.Issued> getIssuedCoupons(Long couponId, Integer page, Integer size, ZonedDateTime now) {
        return couponService.getIssuedCoupons(couponId, page, size).stream()
            .map(issuedCoupon -> CouponInfo.Issued.from(issuedCoupon, now))
            .toList();
    }
}
