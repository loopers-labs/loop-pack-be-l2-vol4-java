package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.UserCouponModel;
import com.loopers.domain.coupon.UserCouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class UserCouponRepositoryImpl implements UserCouponRepository {

    private final UserCouponJpaRepository userCouponJpaRepository;

    @Override
    public UserCouponModel save(UserCouponModel userCoupon) {
        return userCouponJpaRepository.save(userCoupon);
    }

    @Override
    public Optional<UserCouponModel> findById(Long id) {
        return userCouponJpaRepository.findById(id);
    }

    @Override
    public Optional<UserCouponModel> findByIdWithCoupon(Long id) {
        return userCouponJpaRepository.findByIdWithCoupon(id);
    }

    @Override
    public Page<UserCouponModel> findAllByUserId(Long userId, Pageable pageable) {
        return userCouponJpaRepository.findAllByUserId(userId, pageable);
    }

    @Override
    public Page<UserCouponModel> findAllByCouponId(Long couponId, Pageable pageable) {
        return userCouponJpaRepository.findAllByCouponId(couponId, pageable);
    }

    @Override
    public boolean existsByUserIdAndCouponId(Long userId, Long couponId) {
        return userCouponJpaRepository.existsByUserIdAndCouponId(userId, couponId);
    }
}
