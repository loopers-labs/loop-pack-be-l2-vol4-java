package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CouponJpaRepository extends JpaRepository<CouponModel, Long> {
    Optional<CouponModel> findByIdAndDeletedAtIsNull(Long id);
}
