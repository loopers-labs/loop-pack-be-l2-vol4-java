package com.loopers.domain.coupon;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface IssuedCouponRepository {

    Page<IssuedCouponModel> findAllByCouponTemplateId(Long couponTemplateId, Pageable pageable);

    List<IssuedCouponModel> findAllByUserId(Long userId);

    Optional<IssuedCouponModel> findById(Long id);

    IssuedCouponModel save(IssuedCouponModel issuedCoupon);
}
