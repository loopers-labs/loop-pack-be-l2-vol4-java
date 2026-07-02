package com.loopers.domain.coupon;

import java.util.Optional;

public interface CouponRepository {
    Optional<Coupon> find(Long id);
    int tryConsumeQuantity(Long couponId); // 1=성공, 0=소진/비선착순
}
