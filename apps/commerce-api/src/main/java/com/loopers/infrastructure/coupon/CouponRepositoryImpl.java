package com.loopers.infrastructure.coupon;

import org.springframework.stereotype.Component;

import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponRepository;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Component
public class CouponRepositoryImpl implements CouponRepository {

    private final CouponJpaRepository couponJpaRepository;

    @Override
    public CouponModel save(CouponModel coupon) {
        return couponJpaRepository.save(coupon);
    }
}
