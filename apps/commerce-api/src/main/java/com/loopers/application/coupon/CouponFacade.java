package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.CouponType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class CouponFacade {

    private final CouponService couponService;

    @Transactional
    public CouponInfo issue(Long userId, Long couponPolicyId) {
        return CouponInfo.from(couponService.issue(userId, couponPolicyId), ZonedDateTime.now());
    }

    @Transactional(readOnly = true)
    public List<CouponInfo> getMyCoupons(Long userId) {
        ZonedDateTime now = ZonedDateTime.now();
        return couponService.getMyCoupons(userId).stream()
            .map(userCoupon -> CouponInfo.from(userCoupon, now))
            .toList();
    }

    @Transactional
    public CouponAdminInfo createPolicy(String name, CouponType type, long value, Long minOrderAmount, ZonedDateTime expiredAt) {
        return CouponAdminInfo.from(couponService.createPolicy(name, type, value, minOrderAmount, expiredAt));
    }

    @Transactional(readOnly = true)
    public CouponAdminInfo getPolicy(Long couponPolicyId) {
        return CouponAdminInfo.from(couponService.getPolicy(couponPolicyId));
    }

    @Transactional(readOnly = true)
    public Page<CouponAdminInfo> getPolicies(int page, int size) {
        return couponService.getPolicies(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")))
            .map(CouponAdminInfo::from);
    }

    @Transactional
    public CouponAdminInfo updatePolicy(Long couponPolicyId, String name, Long minOrderAmount, ZonedDateTime expiredAt) {
        return CouponAdminInfo.from(couponService.updatePolicy(couponPolicyId, name, minOrderAmount, expiredAt));
    }

    @Transactional
    public void deletePolicy(Long couponPolicyId) {
        couponService.deletePolicy(couponPolicyId);
    }

    @Transactional(readOnly = true)
    public Page<IssuedCouponInfo> getIssuedCoupons(Long couponPolicyId, int page, int size) {
        ZonedDateTime now = ZonedDateTime.now();
        return couponService.getIssuedCoupons(couponPolicyId, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")))
            .map(userCoupon -> IssuedCouponInfo.from(userCoupon, now));
    }
}
