package com.loopers.domain.coupon.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserCouponStatus {
    ISSUED("발급"),
    USED("사용됨");

    private final String description;
}
