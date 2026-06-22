package com.loopers.domain.coupon;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.Optional;

public interface CouponTemplateRepository {
    CouponTemplateModel save(CouponTemplateModel template);
    Optional<CouponTemplateModel> findById(Long id);
    Page<CouponTemplateModel> findAll(PageRequest pageRequest);
}
