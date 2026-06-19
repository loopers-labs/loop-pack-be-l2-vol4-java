package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponTemplateModel;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CouponTemplateJpaRepository extends JpaRepository<CouponTemplateModel, Long> {
}
