package com.loopers.domain.coupon;

import java.util.List;
import java.util.Optional;

public interface CouponTemplateRepository {
    CouponTemplate save(CouponTemplate couponTemplate);

    Optional<CouponTemplate> find(Long couponTemplateId);

    Optional<CouponTemplate> findActiveForUpdate(Long couponTemplateId);

    List<CouponTemplate> findAll(int page, int size);

    long countAll();
}
