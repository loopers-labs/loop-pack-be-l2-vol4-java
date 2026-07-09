package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponIssueRequestModel;
import com.loopers.domain.coupon.CouponIssueRequestStatus;

import java.time.ZonedDateTime;

/** 선착순 발급 요청 접수/결과 응답용 (Slice 4). */
public record CouponIssueRequestInfo(
        Long requestId,
        Long userId,
        Long couponId,
        CouponIssueRequestStatus status,
        ZonedDateTime requestedAt,
        ZonedDateTime processedAt
) {
    public static CouponIssueRequestInfo from(CouponIssueRequestModel model) {
        return new CouponIssueRequestInfo(
                model.getId(),
                model.getUserId(),
                model.getCouponId(),
                model.getStatus(),
                model.getRequestedAt(),
                model.getProcessedAt()
        );
    }
}
