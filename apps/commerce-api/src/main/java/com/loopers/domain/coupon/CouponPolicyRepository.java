package com.loopers.domain.coupon;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CouponPolicyRepository {
    CouponPolicy save(CouponPolicy couponPolicy);
    Optional<CouponPolicy> findById(Long id);
    Optional<CouponPolicy> findActiveById(Long id);
    Page<CouponPolicy> findAll(Pageable pageable);
    List<CouponPolicy> findAllByIdIn(Collection<Long> ids);
}
