package com.loopers.application.coupon;

import java.time.ZonedDateTime;
import java.util.List;

public interface UserCouponListQuery {

    List<UserCouponInfo> findMyCoupons(Long userId, ZonedDateTime now);
}
