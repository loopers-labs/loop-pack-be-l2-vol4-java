package com.loopers.domain.coupon;

import com.loopers.support.pagination.PageQuery;
import com.loopers.support.pagination.PageResult;

import java.time.ZonedDateTime;
import java.util.Optional;

public interface UserCouponRepository {

    UserCoupon save(UserCoupon userCoupon);

    Optional<UserCoupon> findById(Long userCouponId);

    Optional<UserCoupon> findIssuedCoupon(Long userId, Long couponTemplateId);

    PageResult<UserCoupon> findAllByCouponTemplateId(Long couponTemplateId, PageQuery query);

    boolean useAvailableCoupon(Long userCouponId, Long userId, ZonedDateTime usedAt);
}
