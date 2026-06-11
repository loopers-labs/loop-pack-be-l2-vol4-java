package com.loopers.interfaces.api.admin.coupon;

import com.loopers.application.coupon.CouponInfo;
import com.loopers.application.coupon.CouponIssueInfo;
import com.loopers.domain.coupon.CouponType;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;

public class AdminCouponV1Dto {

    public record CreateCouponRequest(
            String name,
            CouponType type,
            Long value,
            Long minOrderAmount,
            LocalDateTime expiredAt,
            Integer quantity
    ) {}

    public record UpdateCouponRequest(
            String name,
            CouponType type,
            Long value,
            Long minOrderAmount,
            LocalDateTime expiredAt
    ) {}

    public record CouponResponse(
            Long id,
            String name,
            CouponType type,
            Long value,
            Long minOrderAmount,
            LocalDateTime expiredAt,
            Integer quantity
    ) {
        public static CouponResponse from(CouponInfo info) {
            return new CouponResponse(
                    info.id(),
                    info.name(),
                    info.type(),
                    info.value(),
                    info.minOrderAmount(),
                    info.expiredAt(),
                    info.quantity()
            );
        }
    }

    public record CouponIssueResponse(
            Long userCouponId,
            Long userId,
            Long couponId,
            boolean used,
            ZonedDateTime usedAt,
            ZonedDateTime issuedAt
    ) {
        public static CouponIssueResponse from(CouponIssueInfo info) {
            return new CouponIssueResponse(
                    info.userCouponId(),
                    info.userId(),
                    info.couponId(),
                    info.used(),
                    info.usedAt(),
                    info.issuedAt()
            );
        }
    }
}