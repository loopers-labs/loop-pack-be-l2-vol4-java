package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.model.CouponTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CouponTemplateJpaRepository extends JpaRepository<CouponTemplate, Long> {
}
