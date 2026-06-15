package com.loopers.domain.coupon;

import java.util.List;
import java.util.Optional;

public interface CouponRepository {
    CouponModel save(CouponModel coupon);
    Optional<CouponModel> findById(Long id);
    List<CouponModel> findAll(int page, int size);   // ADMIN 템플릿 목록 (최신순)
}
