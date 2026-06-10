package com.loopers.infrastructure.coupon;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import com.loopers.domain.coupon.UserCouponModel;
import com.loopers.domain.coupon.UserCouponRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

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

    @Override
    public List<UserCouponModel> findByUserIdOrderByCreatedAtDesc(Long userId) {
        return userCouponJpaRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Override
    public Page<UserCouponModel> findByCouponIdOrderByCreatedAtDesc(Long couponId, int page, int size) {
        return userCouponJpaRepository.findByCouponIdOrderByCreatedAtDesc(couponId, PageRequest.of(page, size));
    }

    @Override
    public UserCouponModel getActiveByIdAndUserId(Long userCouponId, Long userId) {
        return userCouponJpaRepository.findByIdAndUserIdAndDeletedAtIsNull(userCouponId, userId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "발급 쿠폰이 존재하지 않습니다."));
    }
}
