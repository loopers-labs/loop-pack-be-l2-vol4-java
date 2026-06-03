package com.loopers.interfaces.api.admin;

import com.loopers.application.coupon.CouponInfo;
import com.loopers.application.coupon.IssuedCouponInfo;
import com.loopers.domain.coupon.CouponType;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;

public class AdminCouponV1Dto {

    public record CreateCouponRequest(
        String name,
        String type,
        long value,
        Long minOrderAmount,
        LocalDateTime expiredAt
    ) {}

    public record UpdateCouponRequest(
        String name,
        long value,
        Long minOrderAmount,
        LocalDateTime expiredAt
    ) {}

    public record CouponResponse(
        Long id,
        String name,
        String type,
        long value,
        Long minOrderAmount,
        ZonedDateTime expiredAt,
        boolean active
    ) {
        public static CouponResponse from(CouponInfo info) {
            return new CouponResponse(
                info.id(),
                info.name(),
                info.type().name(),
                info.value(),
                info.minOrderAmount(),
                info.expiredAt(),
                info.active()
            );
        }
    }

    public record IssueResponse(
        Long userCouponId,
        Long userId,
        Long couponId,
        String status,
        ZonedDateTime issuedAt,
        ZonedDateTime usedAt
    ) {
        public static IssueResponse from(IssuedCouponInfo info) {
            return new IssueResponse(
                info.userCouponId(),
                info.userId(),
                info.couponId(),
                info.status() == null ? null : info.status().name(),
                info.issuedAt(),
                info.usedAt()
            );
        }
    }

    /** "FIXED"/"RATE" 문자열을 enum으로 — 잘못된 값은 BAD_REQUEST. */
    public static CouponType parseType(String type) {
        if (type == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 할인 방식(type)은 필수입니다.");
        }
        try {
            return CouponType.valueOf(type.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, "알 수 없는 쿠폰 할인 방식입니다: " + type);
        }
    }
}
