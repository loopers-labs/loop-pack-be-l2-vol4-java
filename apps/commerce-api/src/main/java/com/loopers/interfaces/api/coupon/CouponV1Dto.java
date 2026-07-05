package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponResult;
import com.loopers.domain.coupon.CouponIssueRequestStatus;
import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.CouponType;

import java.time.ZonedDateTime;

public class CouponV1Dto {
    public record CouponIssueRequestResponse(
        Long requestId,
        Long couponTemplateId,
        String userId,
        CouponIssueRequestStatus status,
        Long issuedCouponId,
        String failureReason,
        ZonedDateTime requestedAt,
        ZonedDateTime completedAt
    ) {
        public static CouponIssueRequestResponse from(CouponResult.IssueRequest result) {
            return new CouponIssueRequestResponse(
                result.requestId(),
                result.couponTemplateId(),
                result.userId(),
                result.status(),
                result.issuedCouponId(),
                result.failureReason(),
                result.requestedAt(),
                result.completedAt()
            );
        }
    }

    public record IssuedCouponResponse(
        Long couponId,
        Long couponTemplateId,
        String couponName,
        CouponType type,
        Long value,
        Long minOrderAmount,
        ZonedDateTime expiredAt,
        CouponStatus status,
        ZonedDateTime issuedAt,
        ZonedDateTime usedAt
    ) {
        public static IssuedCouponResponse from(CouponResult.Issued result) {
            return new IssuedCouponResponse(
                result.couponId(),
                result.couponTemplateId(),
                result.couponName(),
                result.type(),
                result.value(),
                result.minOrderAmount(),
                result.expiredAt(),
                result.status(),
                result.issuedAt(),
                result.usedAt()
            );
        }
    }
}
