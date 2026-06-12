package com.loopers.infrastructure.coupon;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserCouponJpaRepository extends JpaRepository<UserCouponEntity, Long> {
    List<UserCouponEntity> findAllByUserId(Long userId);
    Page<UserCouponEntity> findAllByCouponId(Long couponId, Pageable pageable);
    boolean existsByUserIdAndCouponId(Long userId, Long couponId);
}
