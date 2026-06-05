package com.loopers.interfaces.api.coupon.dto;

import com.loopers.application.coupon.CouponInfo;
import com.loopers.domain.coupon.CouponStatus;

public record IssueCouponV1Response(Long couponId, Long templateId, CouponStatus status) {
    public static IssueCouponV1Response from(CouponInfo info) {
        return new IssueCouponV1Response(info.couponId(), info.templateId(), info.status());
    }
}
