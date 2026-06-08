package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.UserCouponModel;
import com.loopers.domain.coupon.UserCouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
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
    public UserCouponModel saveAndFlush(UserCouponModel userCoupon) {
        return userCouponJpaRepository.saveAndFlush(userCoupon);
    }

    @Override
    public Optional<UserCouponModel> findById(Long id) {
        return userCouponJpaRepository.findById(id);
    }

    @Override
    public boolean existsByUserIdAndCouponId(Long userId, Long couponId) {
        return userCouponJpaRepository.existsByUserIdAndCouponId(userId, couponId);
    }

    @Override
    public boolean existsByCouponId(Long couponId) {
        return userCouponJpaRepository.existsByCouponId(couponId);
    }

    @Override
    public List<UserCouponModel> findByUserId(Long userId) {
        return userCouponJpaRepository.findByUserId(userId);
    }

    @Override
    public List<UserCouponModel> findByCouponId(Long couponId, int page, int size) {
        return userCouponJpaRepository.findByCouponId(couponId, PageRequest.of(page, size));
    }
}
