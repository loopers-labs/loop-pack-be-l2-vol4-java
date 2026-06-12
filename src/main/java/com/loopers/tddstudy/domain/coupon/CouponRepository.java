package com.loopers.tddstudy.domain.coupon;

import java.util.List;
import java.util.Optional;

public interface CouponRepository {
    Coupon save(Coupon coupon);
    Optional<Coupon> findById(Long id);
    List<Coupon> findAll();
    void deleteById(Long id);
}
