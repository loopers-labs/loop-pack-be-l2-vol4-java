package com.loopers.domain.coupon;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface IssuedCouponRepository {
    IssuedCoupon save(IssuedCoupon coupon);
    Optional<IssuedCoupon> findById(Long id);
    List<IssuedCoupon> findAllByUserId(Long userId);
    Page<IssuedCoupon> findByCouponTemplateId(Long couponTemplateId, Pageable pageable);
}
