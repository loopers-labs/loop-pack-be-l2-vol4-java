package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.UserCoupon;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserCouponJpaRepository extends JpaRepository<UserCoupon, Long> {
    List<UserCoupon> findByUserId(Long userId);
    Page<UserCoupon> findByCouponId(Long couponId, Pageable pageable);
    long countByCouponId(Long couponId);
}
