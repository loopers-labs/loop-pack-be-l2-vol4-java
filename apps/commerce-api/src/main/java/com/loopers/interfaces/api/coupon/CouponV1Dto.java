package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponIssueRequestInfo;
import com.loopers.domain.coupon.CouponIssueStatus;

import java.time.ZonedDateTime;

public final class CouponV1Dto {

    private CouponV1Dto() {}

    public record IssueRequestResponse(
        String requestId,
        CouponIssueStatus status,
        Long userCouponId,
        String rejectReason,
        ZonedDateTime resolvedAt
    ) {
        public static IssueRequestResponse from(CouponIssueRequestInfo info) {
            return new IssueRequestResponse(
                info.requestId(),
                info.status(),
                info.userCouponId(),
                info.rejectReason(),
                info.resolvedAt()
            );
        }
    }
}
