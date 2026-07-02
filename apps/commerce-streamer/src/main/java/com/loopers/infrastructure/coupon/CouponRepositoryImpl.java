package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class CouponRepositoryImpl implements CouponRepository {

    private final CouponJpaRepository jpaRepository;

    @Override
    public Optional<Coupon> find(Long id) { return jpaRepository.findById(id); }

    @Override
    public int tryConsumeQuantity(Long couponId) { return jpaRepository.tryConsumeQuantity(couponId); }
}
