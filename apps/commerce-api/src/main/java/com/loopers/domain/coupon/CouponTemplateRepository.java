package com.loopers.domain.coupon;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface CouponTemplateRepository {

    Optional<CouponTemplateModel> findById(Long id);

    List<CouponTemplateModel> findAllByIds(Set<Long> ids);

    Page<CouponTemplateModel> findAll(Pageable pageable);

    CouponTemplateModel save(CouponTemplateModel couponTemplate);
}
