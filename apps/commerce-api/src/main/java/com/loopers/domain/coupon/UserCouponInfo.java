package com.loopers.domain.coupon;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record UserCouponInfo(
        Long id,
        Long couponTemplateId,
        String name,
        CouponType type,
        BigDecimal value,
        BigDecimal minOrderAmount,
        BigDecimal maxDiscountAmount,
        String status, // AVAILABLE | USED | EXPIRED
        LocalDateTime expiredAt
) {
    public static UserCouponInfo of(CouponIssue issue, LocalDateTime now) {
        String status = issue.getStatus().name();
        if (issue.getStatus() == CouponStatus.AVAILABLE && issue.isExpired(now)) {
            status = "EXPIRED";
        }
        return new UserCouponInfo(
                issue.getId(),
                issue.getCouponTemplateId(),
                issue.getCouponName(),
                issue.getCouponType(),
                issue.getDiscountValue(),
                issue.getMinOrderAmount(),
                issue.getMaxDiscountAmount(),
                status,
                issue.getExpiredAt()
        );
    }
}
