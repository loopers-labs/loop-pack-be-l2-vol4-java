package com.loopers.coupon.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CouponRepository {

    Coupon save(Coupon coupon);

    Optional<Coupon> find(Long id);

    List<Coupon> findAll();

    List<Coupon> findAllByIds(Collection<Long> ids);
}
