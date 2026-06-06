package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.UserCoupon;
import com.loopers.domain.coupon.UserCouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class UserCouponRepositoryImpl implements UserCouponRepository {

    private final UserCouponJpaRepository userCouponJpaRepository;

    @Override
    public UserCoupon save(UserCoupon userCoupon) {
        return userCouponJpaRepository.save(userCoupon);
    }

    @Override
    public Optional<UserCoupon> findById(Long id) {
        return userCouponJpaRepository.findById(id);
    }

    @Override
    public List<UserCoupon> findByUserId(Long userId) {
        return userCouponJpaRepository.findByUserId(userId);
    }

    @Override
    public Page<UserCoupon> findByCouponPolicyId(Long couponPolicyId, Pageable pageable) {
        return userCouponJpaRepository.findByCouponPolicyId(couponPolicyId, pageable);
    }
}
