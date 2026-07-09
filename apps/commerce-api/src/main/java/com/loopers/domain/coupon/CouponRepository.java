package com.loopers.domain.coupon;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface CouponRepository {
    CouponModel save(CouponModel coupon);
    Optional<CouponModel> findById(Long id);
    Page<CouponModel> findAllActive(Pageable pageable);

    /** 발급 가능(수량 제한 없음 또는 잔여 수량 있음)할 때만 issuedQuantity를 원자적으로 1 증가시킨다. affected=0이면 품절. */
    int incrementIssuedQuantity(Long couponId);
}
