package com.loopers.domain.coupon;

import com.loopers.support.pagination.PageQuery;
import com.loopers.support.pagination.PageResult;

import java.util.Optional;

public interface CouponTemplateRepository {

    CouponTemplate save(CouponTemplate couponTemplate);

    Optional<CouponTemplate> findActiveById(Long couponTemplateId);

    PageResult<CouponTemplate> findActiveAll(PageQuery query);
}
