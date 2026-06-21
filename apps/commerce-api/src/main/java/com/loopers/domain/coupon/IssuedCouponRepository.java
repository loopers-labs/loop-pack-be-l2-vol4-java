package com.loopers.domain.coupon;

import java.util.List;
import java.util.Optional;

public interface IssuedCouponRepository {
    IssuedCoupon save(IssuedCoupon issuedCoupon);

    Optional<IssuedCoupon> findForUpdate(Long issuedCouponId);

    List<IssuedCoupon> findByUserId(String userId, int page, int size);

    long countByUserId(String userId);

    List<IssuedCoupon> findByCouponTemplateId(Long couponTemplateId, int page, int size);

    long countByCouponTemplateId(Long couponTemplateId);

    long countByCouponTemplateIdAndUserId(Long couponTemplateId, String userId);
}
