package com.loopers.domain.coupon;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface CouponRepository {

    CouponModel save(CouponModel coupon);

    Optional<CouponModel> find(Long id);

    Optional<CouponModel> findByIdForUpdate(Long id);

    Page<CouponModel> search(Pageable pageable);
}