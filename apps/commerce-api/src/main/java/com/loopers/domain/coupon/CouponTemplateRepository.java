package com.loopers.domain.coupon;

import com.loopers.domain.common.PageResult;

import java.util.Optional;

public interface CouponTemplateRepository {

    Optional<CouponTemplate> find(Long id);
    PageResult<CouponTemplate> findAll(int page, int size);
    CouponTemplate save(CouponTemplate template);

}
