package com.loopers.coupon.interfaces.api;

import com.loopers.coupon.application.CouponInfo;
import com.loopers.coupon.application.CouponIssueInfo;
import com.loopers.coupon.application.CreateCouponCommand;
import com.loopers.coupon.application.UpdateCouponCommand;
import com.loopers.coupon.domain.CouponType;
import com.loopers.coupon.domain.UserCouponStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.ZonedDateTime;

public class CouponAdminV1Dto {

    public record CreateCouponRequest(
        @NotBlank(message = "쿠폰 이름은 비어있을 수 없습니다.")
        String name,

        @NotNull(message = "쿠폰 타입은 비어있을 수 없습니다.")
        CouponType type,

        @NotNull(message = "쿠폰 할인 값은 비어있을 수 없습니다.")
        @Positive(message = "쿠폰 할인 값은 0보다 커야 합니다.")
        Long value,

        @PositiveOrZero(message = "최소 주문 금액은 0 이상이어야 합니다.")
        Long minOrderAmount,

        @NotNull(message = "쿠폰 만료일은 비어있을 수 없습니다.")
        ZonedDateTime expiredAt
    ) {
        public CreateCouponCommand toCommand() {
            return new CreateCouponCommand(name, type, value, minOrderAmount, expiredAt);
        }
    }

    public record UpdateCouponRequest(
        @NotBlank(message = "쿠폰 이름은 비어있을 수 없습니다.")
        String name,

        @NotNull(message = "쿠폰 타입은 비어있을 수 없습니다.")
        CouponType type,

        @NotNull(message = "쿠폰 할인 값은 비어있을 수 없습니다.")
        @Positive(message = "쿠폰 할인 값은 0보다 커야 합니다.")
        Long value,

        @PositiveOrZero(message = "최소 주문 금액은 0 이상이어야 합니다.")
        Long minOrderAmount,

        @NotNull(message = "쿠폰 만료일은 비어있을 수 없습니다.")
        ZonedDateTime expiredAt
    ) {
        public UpdateCouponCommand toCommand(Long couponId) {
            return new UpdateCouponCommand(couponId, name, type, value, minOrderAmount, expiredAt);
        }
    }

    public record CouponResponse(
        Long id,
        String name,
        CouponType type,
        long discountValue,
        Long minimumOrderAmount,
        ZonedDateTime expiredAt,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt,
        ZonedDateTime deletedAt
    ) {
        public static CouponResponse from(CouponInfo info) {
            return new CouponResponse(
                info.id(),
                info.name(),
                info.type(),
                info.discountValue(),
                info.minimumOrderAmount(),
                info.expiredAt(),
                info.createdAt(),
                info.updatedAt(),
                info.deletedAt()
            );
        }
    }

    public record CouponIssueResponse(
        Long userCouponId,
        Long userId,
        UserCouponStatus status,
        ZonedDateTime issuedAt,
        ZonedDateTime usedAt
    ) {
        public static CouponIssueResponse from(CouponIssueInfo info) {
            return new CouponIssueResponse(
                info.userCouponId(),
                info.userId(),
                info.status(),
                info.issuedAt(),
                info.usedAt()
            );
        }
    }
}
