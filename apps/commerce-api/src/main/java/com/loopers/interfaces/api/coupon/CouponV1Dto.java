package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponIssueRequestInfo;
import com.loopers.application.coupon.IssuedCouponInfo;

import java.time.ZonedDateTime;

public class CouponV1Dto {

    public record IssuedCouponResponse(
        Long userCouponId,
        Long couponId,
        String couponName,
        String type,
        long value,
        Long minOrderAmount,
        String status,
        ZonedDateTime issuedAt,
        ZonedDateTime usedAt,
        ZonedDateTime expiredAt
    ) {
        public static IssuedCouponResponse from(IssuedCouponInfo info) {
            return new IssuedCouponResponse(
                info.userCouponId(),
                info.couponId(),
                info.couponName(),
                info.type() == null ? null : info.type().name(),
                info.value(),
                info.minOrderAmount(),
                info.status() == null ? null : info.status().name(),
                info.issuedAt(),
                info.usedAt(),
                info.expiredAt()
            );
        }
    }

    /** 선착순 발급 요청 접수 응답 (202) — requestId로 이후 결과를 조회한다. */
    public record IssueRequestAcceptedResponse(
        Long requestId,
        Long couponId,
        String status
    ) {
        public static IssueRequestAcceptedResponse from(CouponIssueRequestInfo info) {
            return new IssueRequestAcceptedResponse(
                info.requestId(),
                info.couponId(),
                info.status() == null ? null : info.status().name()
            );
        }
    }

    /** 선착순 발급 요청 결과 응답. */
    public record IssueResultResponse(
        Long requestId,
        Long couponId,
        String status,
        ZonedDateTime requestedAt,
        ZonedDateTime processedAt
    ) {
        public static IssueResultResponse from(CouponIssueRequestInfo info) {
            return new IssueResultResponse(
                info.requestId(),
                info.couponId(),
                info.status() == null ? null : info.status().name(),
                info.requestedAt(),
                info.processedAt()
            );
        }
    }
}
