package com.loopers.infrastructure.coupon;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IssuedCouponJpaRepository extends JpaRepository<IssuedCouponEntity, Long> {
    List<IssuedCouponEntity> findAllByUserId(Long userId);
    Page<IssuedCouponEntity> findAllByCouponId(Long couponId, Pageable pageable);
}
