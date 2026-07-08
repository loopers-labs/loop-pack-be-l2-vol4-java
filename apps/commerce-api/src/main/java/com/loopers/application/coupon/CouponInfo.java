package com.loopers.application.coupon;

import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.IssuedCoupon;

import java.time.ZonedDateTime;

public final class CouponInfo {

    private CouponInfo() {}

    public record Template(
        Long id,
        String name,
        CouponType type,
        Long value,
        Long minOrderAmount,
        ZonedDateTime expiredAt,
        Long issueLimit,
        Long issuedCount
    ) {
        public static Template from(Coupon coupon) {
            return new Template(
                coupon.getId(),
                coupon.getName(),
                coupon.getType(),
                coupon.getValue(),
                coupon.getMinOrderAmount(),
                coupon.getExpiredAt(),
                coupon.getIssueLimit(),
                coupon.getIssuedCount()
            );
        }
    }

    public record FirstComeIssueRequest(
        String requestId,
        Long couponId,
        String userLoginId,
        com.loopers.domain.coupon.IssueRequestStatus status,
        ZonedDateTime requestedAt
    ) {
        public static FirstComeIssueRequest from(com.loopers.domain.coupon.CouponIssueRequest request) {
            return new FirstComeIssueRequest(
                request.getRequestId(),
                request.getCouponId(),
                request.getUserLoginId(),
                request.getStatus(),
                request.getRequestedAt()
            );
        }
    }

    public record FirstComeIssueResult(
        String requestId,
        Long couponId,
        String userLoginId,
        com.loopers.domain.coupon.IssueRequestStatus status,
        com.loopers.domain.coupon.IssueRejectReason rejectReason,
        Long issuedCouponId,
        ZonedDateTime requestedAt,
        ZonedDateTime processedAt
    ) {
        public static FirstComeIssueResult from(com.loopers.domain.coupon.CouponIssueRequest request) {
            return new FirstComeIssueResult(
                request.getRequestId(),
                request.getCouponId(),
                request.getUserLoginId(),
                request.getStatus(),
                request.getRejectReason(),
                request.getIssuedCouponId(),
                request.getRequestedAt(),
                request.getProcessedAt()
            );
        }
    }

    public record Issued(
        Long id,
        Long couponId,
        String userLoginId,
        CouponStatus status,
        ZonedDateTime expiredAt,
        ZonedDateTime usedAt
    ) {
        public static Issued from(IssuedCoupon issuedCoupon, ZonedDateTime now) {
            return new Issued(
                issuedCoupon.getId(),
                issuedCoupon.getCouponId(),
                issuedCoupon.getUserLoginId(),
                issuedCoupon.currentStatus(now),
                issuedCoupon.getExpiredAt(),
                issuedCoupon.getUsedAt()
            );
        }
    }
}
