package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.CouponIssueRequest;
import com.loopers.domain.coupon.CouponIssueRequestStatus;
import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.IssuedCoupon;

import java.time.ZonedDateTime;

public class CouponResult {
    public record Template(
        Long couponId,
        String name,
        CouponType type,
        Long value,
        Long minOrderAmount,
        Long totalIssueLimit,
        Integer maxIssuesPerUser,
        ZonedDateTime expiredAt,
        boolean active,
        ZonedDateTime createdAt
    ) {
        public static Template from(CouponTemplate couponTemplate) {
            return new Template(
                couponTemplate.getId(),
                couponTemplate.getName(),
                couponTemplate.getType(),
                couponTemplate.getValue(),
                couponTemplate.getMinOrderAmount(),
                couponTemplate.getTotalIssueLimit(),
                couponTemplate.getMaxIssuesPerUser(),
                couponTemplate.getExpiredAt(),
                couponTemplate.isActive(),
                couponTemplate.getCreatedAt()
            );
        }
    }

    public record Issued(
        Long couponId,
        Long couponTemplateId,
        String couponName,
        String userId,
        CouponType type,
        Long value,
        Long minOrderAmount,
        ZonedDateTime expiredAt,
        CouponStatus status,
        ZonedDateTime issuedAt,
        ZonedDateTime usedAt
    ) {
        public static Issued from(IssuedCoupon issuedCoupon, String couponName, ZonedDateTime now) {
            return new Issued(
                issuedCoupon.getId(),
                issuedCoupon.getCouponTemplateId(),
                couponName,
                issuedCoupon.getUserId(),
                issuedCoupon.getType(),
                issuedCoupon.getValue(),
                issuedCoupon.getMinOrderAmount(),
                issuedCoupon.getExpiredAt(),
                issuedCoupon.getStatus(now),
                issuedCoupon.getCreatedAt(),
                issuedCoupon.getUsedAt()
            );
        }
    }

    public record IssueRequest(
        Long requestId,
        Long couponTemplateId,
        String userId,
        CouponIssueRequestStatus status,
        Long issuedCouponId,
        String failureReason,
        ZonedDateTime requestedAt,
        ZonedDateTime completedAt
    ) {
        public static IssueRequest from(CouponIssueRequest request) {
            return new IssueRequest(
                request.getId(),
                request.getCouponTemplateId(),
                request.getUserId(),
                request.getStatus(),
                request.getIssuedCouponId(),
                request.getFailureReason(),
                request.getCreatedAt(),
                request.getCompletedAt()
            );
        }
    }
}
