package com.loopers.tddstudy.infrastructure.coupon;

import com.loopers.tddstudy.domain.coupon.UserCoupon;
import com.loopers.tddstudy.domain.coupon.UserCouponRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class UserCouponRepositoryImpl  implements UserCouponRepository {

    private final UserCouponJpaRepository userCouponJpaRepository;


    public UserCouponRepositoryImpl(UserCouponJpaRepository userCouponJpaRepository) {
        this.userCouponJpaRepository = userCouponJpaRepository;
    }

    @Override
    public UserCoupon save(UserCoupon userCoupon) {
        return userCouponJpaRepository.save(userCoupon);
    }

    @Override
    public Optional<UserCoupon> findById(Long id) {
        return userCouponJpaRepository.findById(id);
    }

    @Override
    public Optional<UserCoupon> findByIdWithLock(Long id) {
        return userCouponJpaRepository.findWithLockById(id);
    }

    @Override
    public List<UserCoupon> findAllByUserId(Long userId) {
        return userCouponJpaRepository.findAllByUserId(userId);
    }


    @Override
    public boolean existsByUserIdAndCouponId(Long userId, Long couponId) {
        return userCouponJpaRepository.existsByUserIdAndCouponId(userId, couponId);
    }

    @Override
    public List<UserCoupon> findAllByCouponId(Long couponId) {
        return userCouponJpaRepository.findAllByCouponId(couponId);
    }
}
