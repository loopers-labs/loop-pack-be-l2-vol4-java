package com.loopers.domain.coupon;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CouponTemplateRepository {
    CouponTemplate save(CouponTemplate template);
    Optional<CouponTemplate> findById(Long id);
    List<CouponTemplate> findAllByIds(Collection<Long> ids);
    Page<CouponTemplate> findAll(Pageable pageable);
    void deleteById(Long id);
}
