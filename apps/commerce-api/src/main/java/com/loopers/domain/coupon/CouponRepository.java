package com.loopers.domain.coupon;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface CouponRepository {
    CouponEntity save(CouponEntity coupon);
    Optional<CouponEntity> findById(Long id);
    Optional<CouponEntity> findByIdWithLock(Long id);
    Page<CouponEntity> findAllByUserId(Long userId, Pageable pageable);
    Page<CouponEntity> findAllByCouponTemplateId(Long couponTemplateId, Pageable pageable);
    void softDeleteAllByTemplateId(Long couponTemplateId);
}
