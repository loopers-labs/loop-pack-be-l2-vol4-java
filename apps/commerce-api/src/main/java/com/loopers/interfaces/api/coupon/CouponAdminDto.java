package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponResult;
import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.CouponType;

import java.time.ZonedDateTime;

public class CouponAdminDto {
    public record UpsertCouponTemplateRequest(
        String name,
        CouponType type,
        Long value,
        Long minOrderAmount,
        Long totalIssueLimit,
        Integer maxIssuesPerUser,
        ZonedDateTime expiredAt
    ) {}

    public record CouponTemplateResponse(
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
        public static CouponTemplateResponse from(CouponResult.Template result) {
            return new CouponTemplateResponse(
                result.couponId(),
                result.name(),
                result.type(),
                result.value(),
                result.minOrderAmount(),
                result.totalIssueLimit(),
                result.maxIssuesPerUser(),
                result.expiredAt(),
                result.active(),
                result.createdAt()
            );
        }
    }

    public record CouponIssueResponse(
        Long couponId,
        String userId,
        CouponStatus status,
        ZonedDateTime issuedAt,
        ZonedDateTime usedAt,
        ZonedDateTime expiredAt
    ) {
        public static CouponIssueResponse from(CouponResult.Issued result) {
            return new CouponIssueResponse(
                result.couponId(),
                result.userId(),
                result.status(),
                result.issuedAt(),
                result.usedAt(),
                result.expiredAt()
            );
        }
    }
}
