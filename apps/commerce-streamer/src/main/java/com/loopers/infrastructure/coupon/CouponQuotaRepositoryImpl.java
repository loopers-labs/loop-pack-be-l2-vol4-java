package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponQuotaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CouponQuotaRepositoryImpl implements CouponQuotaRepository {

    private final CouponQuotaJpaRepository couponQuotaJpaRepository;

    @Override
    public int increaseIssued(Long couponPolicyId) {
        return couponQuotaJpaRepository.increaseIssued(couponPolicyId);
    }
}
