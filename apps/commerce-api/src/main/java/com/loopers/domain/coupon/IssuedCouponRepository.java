package com.loopers.domain.coupon;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface IssuedCouponRepository {

    Page<IssuedCouponModel> findAllByCouponTemplateId(Long couponTemplateId, Pageable pageable);

    List<IssuedCouponModel> findAllByUserId(Long userId);

    IssuedCouponModel save(IssuedCouponModel issuedCoupon);
}
