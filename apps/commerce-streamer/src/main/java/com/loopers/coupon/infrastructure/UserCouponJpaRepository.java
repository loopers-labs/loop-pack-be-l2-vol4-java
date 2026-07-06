package com.loopers.coupon.infrastructure;

import com.loopers.coupon.domain.UserCoupon;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserCouponJpaRepository extends JpaRepository<UserCoupon, Long> {

    long countByCouponId(Long couponId);

    boolean existsByCouponIdAndUserId(Long couponId, Long userId);
}
