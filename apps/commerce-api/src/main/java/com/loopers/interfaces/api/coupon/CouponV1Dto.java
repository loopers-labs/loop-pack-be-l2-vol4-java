package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.UserCouponInfo;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.UserCouponStatus;

import java.time.ZonedDateTime;

public class CouponV1Dto {

    public record IssueResponse(
        Long userCouponId,
        Long couponId,
        String couponName,
        CouponType discountType,
        Long discountValue,
        UserCouponStatus status,
        ZonedDateTime issuedAt
    ) {
        public static IssueResponse from(UserCouponInfo info) {
            return new IssueResponse(
                info.userCouponId(),
                info.couponId(),
                info.couponName(),
                info.discountType(),
                info.discountValue(),
                info.status(),
                info.issuedAt()
            );
        }
    }
}
