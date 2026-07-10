package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponIssueRequestInfo;
import com.loopers.application.coupon.MyIssuedCouponInfo;
import com.loopers.domain.coupon.CouponIssueRequestStatus;
import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.CouponType;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

public class CouponV1Dto {

    public enum CouponTypeDto {
        FIXED, RATE;

        public static CouponTypeDto from(CouponType type) {
            return valueOf(type.name());
        }
    }

    public enum CouponStatusDto {
        AVAILABLE, USED, EXPIRED
    }

    public record MyIssuedCouponResponse(
            Long couponId,
            String name,
            CouponTypeDto type,
            BigDecimal value,
            BigDecimal minOrderAmount,
            ZonedDateTime expiredAt,
            CouponStatusDto status
    ) {
        public static MyIssuedCouponResponse from(MyIssuedCouponInfo info) {
            return new MyIssuedCouponResponse(
                    info.id(),
                    info.name(),
                    CouponTypeDto.from(info.type()),
                    info.value(),
                    info.minOrderAmount(),
                    info.expiredAt(),
                    info.status() == CouponStatus.USED
                            ? CouponStatusDto.USED
                            : ZonedDateTime.now().isAfter(info.expiredAt()) ? CouponStatusDto.EXPIRED : CouponStatusDto.AVAILABLE
            );
        }
    }

    public enum CouponIssueRequestStatusDto {
        PENDING, ISSUED, FAILED;

        public static CouponIssueRequestStatusDto from(CouponIssueRequestStatus status) {
            return valueOf(status.name());
        }
    }

    public record IssueRequestResponse(
            String requestId,
            Long couponTemplateId,
            CouponIssueRequestStatusDto status
    ) {
        public static IssueRequestResponse from(CouponIssueRequestInfo info) {
            return new IssueRequestResponse(
                    info.requestId(),
                    info.couponTemplateId(),
                    CouponIssueRequestStatusDto.from(info.status())
            );
        }
    }

    public record IssueRequestStatusResponse(
            String requestId,
            Long couponTemplateId,
            CouponIssueRequestStatusDto status,
            Long issuedCouponId,
            String failureReason
    ) {
        public static IssueRequestStatusResponse from(CouponIssueRequestInfo info) {
            return new IssueRequestStatusResponse(
                    info.requestId(),
                    info.couponTemplateId(),
                    CouponIssueRequestStatusDto.from(info.status()),
                    info.issuedCouponId(),
                    info.failureReason()
            );
        }
    }
}
