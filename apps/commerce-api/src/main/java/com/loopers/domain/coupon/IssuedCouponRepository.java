package com.loopers.domain.coupon;

import java.util.List;
import java.util.Optional;

public interface IssuedCouponRepository {
    IssuedCoupon save(IssuedCoupon issuedCoupon);
    Optional<IssuedCoupon> find(Long id);
    Optional<IssuedCoupon> findForUpdate(Long id);
    Optional<IssuedCoupon> findByCouponIdAndUserLoginId(Long couponId, String userLoginId);
    Optional<IssuedCoupon> findByCouponIdAndUserLoginIdForUpdate(Long couponId, String userLoginId);
    List<IssuedCoupon> findAllByUserLoginId(String userLoginId);
    List<IssuedCoupon> findAllByCouponId(Long couponId, int page, int size);
}
