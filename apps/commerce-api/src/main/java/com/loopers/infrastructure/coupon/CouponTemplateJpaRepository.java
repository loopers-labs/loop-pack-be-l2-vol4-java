package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CouponTemplateJpaRepository extends JpaRepository<CouponTemplate, Long> {
    List<CouponTemplate> findByIdIn(List<Long> ids);
}
