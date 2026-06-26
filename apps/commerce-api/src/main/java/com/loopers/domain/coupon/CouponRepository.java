package com.loopers.domain.coupon;

import java.util.List;
import java.util.Optional;

public interface CouponRepository {
    Coupon save(Coupon coupon);

    Optional<Coupon> find(Long id);

    List<Coupon> findAll(int page, int size);

    boolean existsById(Long id);

    void delete(Long id);
}
