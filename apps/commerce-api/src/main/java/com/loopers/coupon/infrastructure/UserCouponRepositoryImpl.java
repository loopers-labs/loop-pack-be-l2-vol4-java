package com.loopers.coupon.infrastructure;

import com.loopers.coupon.domain.UserCoupon;
import com.loopers.coupon.domain.UserCouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class UserCouponRepositoryImpl implements UserCouponRepository {

    private final UserCouponJpaRepository userCouponJpaRepository;

    @Override
    public UserCoupon save(UserCoupon userCoupon) {
        return userCouponJpaRepository.save(userCoupon);
    }

    @Override
    public Optional<UserCoupon> findById(Long id) {
        return userCouponJpaRepository.findByIdAndDeletedAtIsNull(id);
    }

    @Override
    public void flush() {
        userCouponJpaRepository.flush();
    }

    @Override
    public boolean existsByCouponIdAndUserId(Long couponId, Long userId) {
        return userCouponJpaRepository.existsByCouponIdAndUserId(couponId, userId);
    }

    @Override
    public List<UserCoupon> findByUserId(Long userId) {
        return userCouponJpaRepository.findByUserIdAndDeletedAtIsNull(userId);
    }

    @Override
    public Page<UserCoupon> findByCouponId(Long couponId, Pageable pageable) {
        return userCouponJpaRepository.findByCouponIdAndDeletedAtIsNull(couponId, pageable);
    }
}
