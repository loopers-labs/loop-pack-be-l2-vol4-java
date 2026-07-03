package com.loopers.domain.coupon;

import java.util.Optional;

public interface CouponStockRepository {
    CouponStockModel save(CouponStockModel couponStock);

    Optional<CouponStockModel> findByCouponId(Long couponId);
}
