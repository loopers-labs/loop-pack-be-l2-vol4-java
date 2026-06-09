package com.loopers.domain.coupon;

import java.util.Optional;

import org.springframework.data.domain.Page;

public interface CouponRepository {

    CouponModel save(CouponModel coupon);

    CouponModel getActiveById(Long id);

    Optional<CouponModel> findActiveById(Long id);

    Page<CouponModel> findActiveByPage(int page, int size);
}
