package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponInfo;
import com.loopers.application.coupon.UserCouponInfo;
import com.loopers.domain.coupon.CouponType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;

public class CouponAdminV1Dto {

    public record CreateCouponRequest(
        @NotBlank(message = "쿠폰명은 필수입니다.") String name,
        @NotNull(message = "쿠폰 타입(FIXED/RATE)은 필수입니다.") CouponType type,
        @Min(value = 0, message = "할인 값은 0 이상이어야 합니다.") long value,
        @Min(value = 0, message = "최소 주문 금액은 0 이상이어야 합니다.") Long minOrderAmount,   // 선택(null 허용)
        @NotNull(message = "만료 시각은 필수입니다.") LocalDateTime expiredAt
    ) {}

    public record UpdateCouponRequest(
        @NotBlank(message = "쿠폰명은 필수입니다.") String name,
        @NotNull(message = "쿠폰 타입(FIXED/RATE)은 필수입니다.") CouponType type,
        @Min(value = 0, message = "할인 값은 0 이상이어야 합니다.") long value,
        @Min(value = 0, message = "최소 주문 금액은 0 이상이어야 합니다.") Long minOrderAmount,
        @NotNull(message = "만료 시각은 필수입니다.") LocalDateTime expiredAt
    ) {}

    public record CouponResponse(
        Long id,
        String name,
        String type,
        long value,
        Long minOrderAmount,
        ZonedDateTime expiredAt
    ) {
        public static CouponResponse from(CouponInfo info) {
            return new CouponResponse(
                info.id(), info.name(), info.type(), info.value(), info.minOrderAmount(), info.expiredAt()
            );
        }
    }

    /** 발급 내역 응답. */
    public record IssueResponse(
        Long id,
        Long couponId,
        String status,
        ZonedDateTime expiredAt,
        ZonedDateTime usedAt
    ) {
        public static IssueResponse from(UserCouponInfo info) {
            return new IssueResponse(info.id(), info.couponId(), info.status(), info.expiredAt(), info.usedAt());
        }
    }
}
