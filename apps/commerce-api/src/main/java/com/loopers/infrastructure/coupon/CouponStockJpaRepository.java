package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponStockModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CouponStockJpaRepository extends JpaRepository<CouponStockModel, Long> {
    Optional<CouponStockModel> findByCouponIdAndDeletedAtIsNull(Long couponId);
}
