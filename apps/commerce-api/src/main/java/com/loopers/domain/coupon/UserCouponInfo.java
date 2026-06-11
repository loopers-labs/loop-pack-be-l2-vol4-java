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
    public static UserCouponInfo of(CouponIssue issue, CouponTemplate template, LocalDateTime now) {
        String status = issue.getStatus().name();
        if (issue.getStatus() == CouponStatus.AVAILABLE && template.isExpired(now)) {
            status = "EXPIRED";
        }
        return new UserCouponInfo(
                issue.getId(),
                template.getId(),
                template.getName(),
                template.getType(),
                template.getValue(),
                template.getMinOrderAmount(),
                template.getMaxDiscountAmount(),
                status,
                template.getExpiredAt()
        );
    }
}
