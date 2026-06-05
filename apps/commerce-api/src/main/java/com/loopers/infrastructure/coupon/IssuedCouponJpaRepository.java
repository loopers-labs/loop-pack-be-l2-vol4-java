package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.IssuedCoupon;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface IssuedCouponJpaRepository extends JpaRepository<IssuedCoupon, Long> {
    List<IssuedCoupon> findAllByUserId(Long userId);
    Page<IssuedCoupon> findByCouponTemplateId(Long couponTemplateId, Pageable pageable);
}
