package com.loopers.domain.coupon;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface CouponTemplateRepository {
    CouponTemplateEntity save(CouponTemplateEntity template);
    Optional<CouponTemplateEntity> findById(Long id);
    Page<CouponTemplateEntity> findAll(Pageable pageable);
}
