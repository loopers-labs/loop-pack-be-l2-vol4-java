package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.UserCouponInfo;

import java.time.ZonedDateTime;

public class CouponV1Dto {

    public record IssueRequestResponse(String requestId) {}

    public record IssueResultResponse(String requestId, Long couponId, String status, Long userCouponId, String reason) {
        public static IssueResultResponse from(com.loopers.application.coupon.CouponIssueResultInfo info) {
            return new IssueResultResponse(info.requestId(), info.couponId(), info.status(),
                info.userCouponId(), info.reason());
        }
    }

    public record UserCouponResponse(
        Long id,
        Long couponId,
        String name,
        String type,
        long value,
        Long minOrderAmount,
        String status,
        ZonedDateTime issuedAt,
        ZonedDateTime usedAt,
        ZonedDateTime expiredAt
    ) {
        public static UserCouponResponse from(UserCouponInfo info) {
            return new UserCouponResponse(
                info.id(),
                info.couponId(),
                info.name(),
                info.type(),
                info.value(),
                info.minOrderAmount(),
                info.status(),
                info.issuedAt(),
                info.usedAt(),
                info.expiredAt()
            );
        }
    }
}
