package com.loopers.domain.coupon;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserCouponStatus {

    AVAILABLE("사용 가능"),
    USED("사용 완료"),
    EXPIRED("만료됨");

    private final String description;
}
