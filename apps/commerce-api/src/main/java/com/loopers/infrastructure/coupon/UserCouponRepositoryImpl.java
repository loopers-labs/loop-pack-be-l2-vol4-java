package com.loopers.infrastructure.coupon;

import org.springframework.stereotype.Component;

import com.loopers.domain.coupon.UserCouponModel;
import com.loopers.domain.coupon.UserCouponRepository;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Component
public class UserCouponRepositoryImpl implements UserCouponRepository {

    private final UserCouponJpaRepository userCouponJpaRepository;

    @Override
    public UserCouponModel save(UserCouponModel userCoupon) {
        return userCouponJpaRepository.save(userCoupon);
    }

    @Override
    public boolean existsByUserIdAndCouponId(Long userId, Long couponId) {
        return userCouponJpaRepository.existsByUserIdAndCouponId(userId, couponId);
    }
}
