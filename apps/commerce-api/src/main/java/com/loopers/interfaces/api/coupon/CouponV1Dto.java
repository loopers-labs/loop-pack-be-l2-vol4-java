package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponIssueRequestInfo;
import com.loopers.application.coupon.UserCouponInfo;

import java.time.ZonedDateTime;

public class CouponV1Dto {

    /** 발급 요청 접수 응답 (202) — requestId 로 결과를 폴링한다. */
    public record IssueAcceptedResponse(String requestId, String status) {
        public static IssueAcceptedResponse from(CouponIssueRequestInfo info) {
            return new IssueAcceptedResponse(info.requestId(), info.status());
        }
    }

    /** 발급 요청 결과 폴링 응답 — PENDING / ISSUED(userCoupon 포함) / FAILED(사유 포함). */
    public record IssueRequestResponse(
        String requestId,
        String status,
        String failureReason,
        UserCouponResponse userCoupon
    ) {
        public static IssueRequestResponse from(CouponIssueRequestInfo info) {
            return new IssueRequestResponse(
                info.requestId(),
                info.status(),
                info.failureReason(),
                info.userCoupon() == null ? null : UserCouponResponse.from(info.userCoupon())
            );
        }
    }

    /** 대고객 - 내 쿠폰/발급 응답. */
    public record UserCouponResponse(
        Long id,
        Long couponId,
        String name,
        String type,
        long value,
        Long minOrderAmount,
        String status,
        ZonedDateTime expiredAt,
        ZonedDateTime usedAt
    ) {
        public static UserCouponResponse from(UserCouponInfo info) {
            return new UserCouponResponse(
                info.id(),
                info.couponId(),
                info.name(),
                info.type(),
                info.value(),
                info.minOrderAmount(),
                info.status(),
                info.expiredAt(),
                info.usedAt()
            );
        }
    }
}
