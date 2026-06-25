package com.loopers.domain.coupon;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface CouponRepository {
    CouponEntity save(CouponEntity coupon);
    Optional<CouponEntity> findById(String id);
    Optional<CouponEntity> findByIdWithLock(String id);
    Page<CouponEntity> findAllByUserId(String userId, Pageable pageable);
    Page<CouponEntity> findAllByCouponTemplateId(String couponTemplateId, Pageable pageable);
    void softDeleteAllByTemplateId(String couponTemplateId);
}
