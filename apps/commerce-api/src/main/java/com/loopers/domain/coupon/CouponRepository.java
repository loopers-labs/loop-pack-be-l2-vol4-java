package com.loopers.domain.coupon;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface CouponRepository {
    Optional<CouponModel> findById(Long id);
    CouponModel save(CouponModel coupon);
    Page<CouponModel> findAll(Pageable pageable);
    void delete(CouponModel coupon);
}
