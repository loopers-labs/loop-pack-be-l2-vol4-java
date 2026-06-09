package com.loopers.domain.coupon;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface CouponTemplateRepository {

    Optional<CouponTemplateModel> findById(Long id);

    Page<CouponTemplateModel> findAll(Pageable pageable);

    CouponTemplateModel save(CouponTemplateModel couponTemplate);
}
