package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.UserCouponModel;
import com.loopers.domain.coupon.UserCouponRepository;
import lombok.RequiredArgsConstructor;
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
    public Optional<UserCouponModel> find(Long id) {
        return userCouponJpaRepository.findByIdAndDeletedAtIsNull(id);
    }

    @Override
    public Optional<UserCouponModel> findByUserIdAndCouponId(Long userId, Long couponId) {
        return userCouponJpaRepository.findByUserIdAndCouponIdAndDeletedAtIsNull(userId, couponId);
    }

    @Override
    public List<UserCouponModel> findAllByUserId(Long userId) {
        return userCouponJpaRepository.findAllByUserIdAndDeletedAtIsNull(userId);
    }

    @Override
    public boolean existsByUserIdAndCouponId(Long userId, Long couponId) {
        return userCouponJpaRepository.existsByUserIdAndCouponIdAndDeletedAtIsNull(userId, couponId);
    }
}
