package com.loopers.domain.coupon;

import java.util.List;
import java.util.Optional;

public interface CouponTemplateRepository {
    CouponTemplate save(CouponTemplate template);
    Optional<CouponTemplate> find(Long id);
    List<CouponTemplate> findAll();
    void delete(Long id);
}
