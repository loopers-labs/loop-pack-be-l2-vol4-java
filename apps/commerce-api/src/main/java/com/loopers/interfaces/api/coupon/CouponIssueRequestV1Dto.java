package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponIssueRequestInfo;
import com.loopers.domain.coupon.CouponIssueRequestStatus;

public class CouponIssueRequestV1Dto {

    public record Response(Long requestId, CouponIssueRequestStatus status) {
        public static Response from(CouponIssueRequestInfo info) {
            return new Response(info.requestId(), info.status());
        }
    }
}
