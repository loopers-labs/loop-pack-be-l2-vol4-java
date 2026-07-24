package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.UserCouponInfo;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.UserCouponStatus;

import java.time.ZonedDateTime;

public class CouponV1Dto {

    /** 선착순 발급 "요청 접수" 응답. 아직 발급된 게 아니라 요청이 큐에 실렸음을 뜻한다. */
    public record IssueRequestResponse(
        String requestId,
        String status
    ) {
        public static IssueRequestResponse accepted(String requestId) {
            return new IssueRequestResponse(requestId, "ACCEPTED");
        }
    }

    /** 발급 요청 상태 조회 응답. status: PENDING / ISSUED / SOLD_OUT. */
    public record IssueRequestStatusResponse(
        String requestId,
        String status
    ) {
    }

    public record IssueResponse(
        Long userCouponId,
        Long couponId,
        String couponName,
        CouponType discountType,
        Long discountValue,
        UserCouponStatus status,
        ZonedDateTime issuedAt
    ) {
        public static IssueResponse from(UserCouponInfo info) {
            return new IssueResponse(
                info.userCouponId(),
                info.couponId(),
                info.couponName(),
                info.discountType(),
                info.discountValue(),
                info.status(),
                info.issuedAt()
            );
        }
    }
}
