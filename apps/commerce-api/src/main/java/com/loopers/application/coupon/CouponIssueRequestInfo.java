package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponIssueRequest;

/**
 * 발급 요청 접수/폴링 결과 DTO.
 *
 * @param userCoupon 발급 성공(ISSUED) 시 발급된 쿠폰 정보. 그 외 null.
 */
public record CouponIssueRequestInfo(
    String requestId,
    String status,
    String failureReason,
    UserCouponInfo userCoupon
) {
    public static CouponIssueRequestInfo from(CouponIssueRequest request, UserCouponInfo userCoupon) {
        return new CouponIssueRequestInfo(
            request.getRequestId(),
            request.getStatus().name(),
            request.getFailureReason(),
            userCoupon
        );
    }
}
