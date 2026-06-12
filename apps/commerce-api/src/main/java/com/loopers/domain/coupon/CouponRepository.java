package com.loopers.domain.coupon;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface CouponRepository {
    CouponModel save(CouponModel coupon);
    Optional<CouponModel> findById(Long id);
    Page<CouponModel> findAll(Pageable pageable);
    void delete(Long id);
}
