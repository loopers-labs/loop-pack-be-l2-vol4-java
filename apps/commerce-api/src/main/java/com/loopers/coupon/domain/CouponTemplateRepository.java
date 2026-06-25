package com.loopers.coupon.domain;

import com.loopers.shared.pagination.PageQuery;
import com.loopers.shared.pagination.PageResult;

import java.util.Optional;

public interface CouponTemplateRepository {

    CouponTemplate save(CouponTemplate couponTemplate);

    Optional<CouponTemplate> findActiveById(Long couponTemplateId);

    PageResult<CouponTemplate> findActiveAll(PageQuery query);
}
