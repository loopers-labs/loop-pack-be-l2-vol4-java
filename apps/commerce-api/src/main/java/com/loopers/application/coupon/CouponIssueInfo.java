package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.UserCoupon;

import java.time.ZonedDateTime;

/**
 * 어드민 발급 내역 뷰 DTO. 특정 템플릿이 누구에게(userId) 언제(issuedAt) 발급됐고 상태가 무엇인지.
 */
public record CouponIssueInfo(
    Long id,
    Long userId,
    CouponStatus status,
    ZonedDateTime issuedAt
) {
    public static CouponIssueInfo from(UserCoupon userCoupon) {
        return new CouponIssueInfo(
            userCoupon.getId(),
            userCoupon.getUserId(),
            userCoupon.getStatus(),
            userCoupon.getCreatedAt()
        );
    }
}
